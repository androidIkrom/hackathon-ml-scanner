package com.nungil.items

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.nungil.R
import com.nungil.contract.Buzz
import com.nungil.contract.Facing
import com.nungil.contract.ItemKind
import com.nungil.contract.Lang
import com.nungil.contract.SavedTab
import com.nungil.contract.VoiceCommand
import com.nungil.contract.app.AppScope
import com.nungil.contract.app.AppServices
import com.nungil.contract.app.VisionFrame
import com.nungil.contract.app.CameraScreen
import com.nungil.contract.app.SwitchableCamera
import com.nungil.contract.app.VoiceHandler
import com.nungil.contract.app.services
import com.nungil.core.items.ItemCrop
import com.nungil.core.items.ItemEnrollmentGuide
import com.nungil.core.items.ItemLook
import com.nungil.core.items.ItemLooks
import com.nungil.core.items.ItemNames
import com.nungil.core.items.ItemPhrases
import com.nungil.core.items.ItemStep
import com.nungil.core.people.EnrollPhrases
import com.nungil.core.people.VectorBytes
import com.nungil.core.walk.RoutePhrases
import com.nungil.data.AppDatabase
import com.nungil.data.ItemEmbeddingEntity
import com.nungil.data.ItemEntity
import com.nungil.databinding.ItemEnrollFragmentBinding
import com.nungil.design.resolveColorAttr
import com.nungil.saved.PhotoFiles
import com.nungil.saved.returnToSaved
import com.nungil.scan.CameraSession
import com.nungil.search.CameraGate
import com.nungil.shell.MainActivity
import com.nungil.speech.TtsSpeaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Item enrolment in two parts.
 *
 * Looking: the thing in the middle of the frame is outlined (ItemSegmenter) and tinted on the preview, and
 * once a few frames agree on what it looks like it is described and the user is asked whether it is the
 * right one: "It is black and round, about 15 centimetres across, about 40 centimetres away.
 * Is this it?" Yes (said, or the button) starts learning; no starts looking again.
 *
 * Learning: 3 samples held still, then 3 each with the phone moved left, right and up (ItemEnrollmentGuide).
 * The thing is followed from frame to frame (the segmenter is asked where it was last seen), and only what
 * still looks like it and is about its size is learned: the bottle behind it is not. A moved step counts only
 * once the thing has really shifted that way from where it was held still. The square around its outline is
 * embedded (ItemWindows.square). Nothing is saved until the last sample.
 *
 * Size and distance come from where the lens is focused, so they are rough. One worker thread does the
 * segmenting and embedding (about half a second a frame); frames that arrive while it is busy are dropped.
 */
class ItemEnrollFragment : Fragment(), VoiceHandler, CameraScreen {
    private var _binding: ItemEnrollFragmentBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<ItemEnrollFragmentArgs>()
    private val gate = CameraGate(this) { startCamera() }
    private val main = Handler(Looper.getMainLooper())

    private lateinit var services: AppServices
    private lateinit var lang: Lang
    private lateinit var name: String
    private lateinit var kind: ItemKind
    private lateinit var appContext: Context

    private enum class Phase { LOOKING, LEARNING, FINISHED }

    @Volatile
    private var phase = Phase.LOOKING

    @Volatile
    private var camera: CameraSession? = null
    private var worker: ExecutorService? = null
    private val busy = AtomicBoolean(false)
    private var maskFill = 0
    private var maskEdge = 0

    /** Learning goes on (not paused). */
    @Volatile
    private var running = false

    /** No samples, no question before this time: the user hears what was said and has time to follow it. */
    @Volatile
    private var waitUntilMs = 0L

    /** When something last happened for the user (a sample, a prompt), so hints are not repeated too soon. */
    @Volatile
    private var lastHintMs = 0L

    /** The user was asked and has not answered; cleared by the answer, or so the question is asked again. */
    @Volatile
    private var asked = false
    private var openedMs = 0L

    // Worker thread only.
    private var sight: ItemSight? = null
    private val guide = ItemEnrollmentGuide()
    private val looks = ArrayDeque<ItemLook>()
    private var trackX: Float? = null
    private var trackY: Float? = null
    private var learningStarted = false
    private var photo: Bitmap? = null
    private var lastSampleMs = 0L
    private var lastSeenMs = 0L

    /** When the step being learned began (0: with the next frame); see STEP_MAX_MS. */
    @Volatile
    private var stepSinceMs = 0L
    private var askedAtMs = 0L
    private var lastLogMs = 0L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = ItemEnrollFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        services = services()
        lang = services.lang
        name = args.name
        kind = args.kind
        appContext = requireContext().applicationContext
        openedMs = SystemClock.elapsedRealtime()
        binding.itemEnrollTitle.text = getString(R.string.item_enroll_title, name)
        ViewCompat.setAccessibilityHeading(binding.itemEnrollTitle, true)
        binding.itemEnrollPrompt.text = ItemPhrases.start(name, lang)
        binding.itemEnrollButton.setText(R.string.item_enroll_yes)
        binding.itemEnrollButton.setOnClickListener {
            when {
                phase == Phase.LOOKING -> confirm()
                running -> pauseLearning()
                else -> resumeLearning()
            }
        }
        gate.attach(binding.itemEnrollPermission)
        val primary = requireContext().resolveColorAttr(R.attr.ngPrimary)
        maskFill = ColorUtils.setAlphaComponent(primary, MASK_FILL_ALPHA)
        maskEdge = primary

        val executor = Executors.newSingleThreadExecutor()
        worker = executor
        executor.execute {
            try {
                sight = ItemSight(appContext)
            } catch (e: Exception) {
                Log.i(TAG, "Item enrolment unavailable", e)
                main.post { modelMissing() }
            }
        }
        services.speaker.say(ItemPhrases.start(name, lang))
    }

    override fun onResume() {
        super.onResume()
        gate.check()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        running = false
        camera?.stop()
        camera = null
        gate.detach()
        (activity as? MainActivity)?.dropWords()
        worker?.let { executor ->
            executor.execute {
                sight?.close()
                sight = null
            }
            // No waiting here: blocking the main thread froze the screen while leaving. The close task above
            // still runs last on the worker thread, and shutdown() lets nothing new in.
            executor.shutdown()
        }
        worker = null
        main.removeCallbacksAndMessages(null)
        _binding = null
    }

    @Volatile private var last: VisionFrame? = null

    override fun lastFrame(): VisionFrame? = last

    /** Items are learned with the back camera only. */
    override val switchable: SwitchableCamera? get() = null

    /** Learning is going on; while it still looks for the item, a stop leaves (nothing was learned to pause). */
    override val isWorking: Boolean get() = phase == Phase.LEARNING && running

    override fun pause() {
        if (_binding != null) pauseLearning()
    }

    override fun resume() {
        if (_binding == null) return
        if (phase == Phase.LOOKING) confirm() else resumeLearning()
    }

    override fun onVoiceCommand(command: VoiceCommand): Boolean = when (command) {
        // "Yes" and "no" are not commands: they come here as plain words while the question is open.
        is VoiceCommand.Unknown -> phase == Phase.LOOKING && asked && answer(command.text)
        else -> false
    }

    private fun startCamera() {
        if (camera != null || _binding == null) return
        val options = CameraSession.Options(facing = Facing.BACK, detect = false, keepBitmap = true)
        camera = CameraSession(this, binding.itemEnrollPreview, options, ::onFrame, ::onCameraError).also { it.start() }
        // Time to hear "Learning (name). Point the camera at it and hold still." and do it.
        waitUntilMs = SystemClock.elapsedRealtime() + START_WAIT_MS
        lastHintMs = waitUntilMs
    }

    // ---- The question -------------------------------------------------------------------------------

    /** Main thread: the question has been asked; the next words are the answer. */
    private fun listenForAnswer() {
        if (_binding == null || phase != Phase.LOOKING) return
        (activity as? MainActivity)?.let {
            it.dictationAccepts = { text -> RoutePhrases.isYes(text) || RoutePhrases.isNo(text) }
            it.dictationEarly = true
        }
        services.askForWords(viewLifecycleOwner) { text -> answer(text) }
    }

    /** Main thread: "yes" starts learning, "no" starts looking again; anything else is asked about again. */
    private fun answer(text: String): Boolean {
        if (phase != Phase.LOOKING) return false
        when {
            RoutePhrases.isYes(text) -> confirm()
            RoutePhrases.isNo(text) -> notThat()
            else -> {
                listenForAnswer()
                return false
            }
        }
        return true
    }

    /** Main thread: the thing in view is the item. Learning starts with the phone held still. */
    private fun confirm() {
        if (phase != Phase.LOOKING || _binding == null) return
        (activity as? MainActivity)?.dropWords()
        phase = Phase.LEARNING
        running = true
        asked = false
        waitUntilMs = SystemClock.elapsedRealtime() + PROMPT_WAIT_MS
        lastHintMs = waitUntilMs
        binding.itemEnrollButton.setText(R.string.item_enroll_pause)
        binding.itemEnrollPrompt.text = ItemPhrases.prompt(ItemStep.STILL, lang)
        val text = ItemPhrases.confirmed(lang)
        (services.speaker as? TtsSpeaker)?.brighten(text)
        services.speaker.sayNow(text)
    }

    /** Main thread: the thing in view is not the item. Looking starts again. */
    private fun notThat() {
        if (phase != Phase.LOOKING) return
        asked = false
        waitUntilMs = SystemClock.elapsedRealtime() + PROMPT_WAIT_MS
        lastHintMs = waitUntilMs
        services.speaker.sayNow(ItemPhrases.notThat(lang))
    }

    private fun pauseLearning() {
        if (phase != Phase.LEARNING || !running) return
        running = false
        binding.itemEnrollButton.setText(R.string.item_enroll_resume)
        services.speaker.say(EnrollPhrases.paused(lang))
    }

    private fun resumeLearning() {
        if (phase != Phase.LEARNING || running) return
        running = true
        // A pause is not time spent on the step.
        stepSinceMs = 0L
        waitUntilMs = SystemClock.elapsedRealtime() + PROMPT_WAIT_MS
        lastHintMs = waitUntilMs
        binding.itemEnrollButton.setText(R.string.item_enroll_pause)
        services.speaker.say(ItemPhrases.prompt(guide.step ?: ItemStep.STILL, lang))
    }

    private fun modelMissing() {
        val b = _binding ?: return
        running = false
        phase = Phase.FINISHED
        b.itemEnrollButton.isEnabled = false
        b.itemEnrollPrompt.setText(R.string.item_enroll_model_missing)
        services.speaker.say(getString(R.string.item_enroll_model_missing))
        services.haptics.buzz(Buzz.ERROR)
    }

    // ---- Frames -------------------------------------------------------------------------------------

    /** Analysis thread: hand the frame to the worker unless it is still busy with an earlier one. */
    private fun onFrame(frame: VisionFrame) {
        last = frame
        if (phase == Phase.FINISHED) return
        val bitmap = frame.bitmap ?: return
        val executor = worker ?: return
        if (!busy.compareAndSet(false, true)) return
        try {
            executor.execute {
                try {
                    process(bitmap, frame.hfovDeg)
                } finally {
                    busy.set(false)
                }
            }
        } catch (e: RejectedExecutionException) {
            busy.set(false)
        }
    }

    /** Worker thread. */
    private fun process(bitmap: Bitmap, hfovDeg: Float) {
        val sight = sight ?: return
        val now = SystemClock.elapsedRealtime()
        // Where the thing was last seen, else the middle of the frame.
        val x = trackX ?: 0.5f
        val y = trackY ?: 0.5f
        val seen = sight.see(bitmap, x, y, hfovDeg, camera?.focusDistanceM)
        showMask(seen, bitmap)
        if (now - lastLogMs >= LOG_MS) {
            lastLogMs = now
            val about = seen?.let { String.format(Locale.US, "%.0f%% of the frame at %.2f,%.2f, %s", it.mask.cover * 100, x, y, it.look) } ?: "nothing"
            Log.i(TAG, "Item $phase: $about, ${SystemClock.elapsedRealtime() - now} ms")
        }
        when (phase) {
            Phase.LOOKING -> looking(seen, now)
            Phase.LEARNING -> learning(seen, bitmap, now)
            Phase.FINISHED -> Unit
        }
    }

    /** Worker thread: tint the thing on the preview, or take the tint away. */
    private fun showMask(seen: ItemSight.Sighting?, bitmap: Bitmap) {
        val tint = seen?.mask?.let {
            val picture = Bitmap.createBitmap(it.pixels(maskFill, maskEdge), it.width, it.height, Bitmap.Config.ARGB_8888)
            // The preview shows the frame; an answer of another shape is stretched back onto it.
            if (it.width == bitmap.width && it.height == bitmap.height) {
                picture
            } else {
                Bitmap.createScaledBitmap(picture, bitmap.width, bitmap.height, true)
            }
        }
        main.post { _binding?.itemEnrollMask?.setImageBitmap(tint) }
    }

    /** Worker thread: gather what the thing in the middle looks like, then ask whether it is the item. */
    private fun looking(seen: ItemSight.Sighting?, now: Long) {
        trackX = null
        trackY = null
        // Nothing there, or a thing so near that it fills the view: it is not asked about until it fits.
        val tooNear = seen != null && ItemEnrollmentGuide.fillsView(seen.mask.cover)
        if (seen == null || tooNear && !asked) {
            looks.clear()
            if (!asked && now >= waitUntilMs && now - lastHintMs >= NO_ITEM_HINT_MS) {
                lastHintMs = now
                val hint = when {
                    tooNear -> ItemPhrases.tooNear(lang)
                    sight?.filled == true -> ItemPhrases.wholeView(lang)
                    else -> ItemPhrases.noItem(lang)
                }
                services.speaker.say(hint)
            }
            return
        }
        looks.addLast(seen.look)
        while (looks.size > LOOKS_KEPT) looks.removeFirst()
        if (asked) {
            // No answer for a while: the thing in view may have changed. Describe what is there now.
            if (now - askedAtMs >= ASK_AGAIN_MS) asked = false
            return
        }
        if (looks.size < LOOKS_TO_ASK || now < waitUntilMs) return
        val look = ItemLooks.agree(looks.toList())
        val question = ItemPhrases.ask(look, lang) ?: return
        asked = true
        askedAtMs = now
        waitUntilMs = Long.MAX_VALUE
        lastHintMs = now
        Log.i(TAG, "Item seen: $look, colours ${seen.colours}, lens focused at ${camera?.focusDistanceM} m")
        main.post { _binding?.itemEnrollPrompt?.text = question }
        (services.speaker as? TtsSpeaker)?.brighten(question)
        services.speaker.sayFinal(question) {
            // The question has been heard: the next words are the answer, and the thing may be described again later.
            waitUntilMs = SystemClock.elapsedRealtime()
            listenForAnswer()
        }
    }

    /** Worker thread: follow the thing and take a sample when it is where the step asks. */
    private fun learning(seen: ItemSight.Sighting?, bitmap: Bitmap, now: Long) {
        if (!learningStarted) {
            learningStarted = true
            guide.restart()
            lastSeenMs = now
            stepSinceMs = now
        }
        if (!running) return
        // A step that is not done in time is left with what it has: the add must end.
        val waiting = guide.step
        if (stepSinceMs == 0L) stepSinceMs = now
        if (waiting != null && waiting != ItemStep.STILL && now - stepSinceMs >= STEP_MAX_MS) {
            Log.i(TAG, "Item step $waiting left after ${(now - stepSinceMs) / 1000} s, ${guide.taken} samples so far")
            guide.skip()
            stepSinceMs = now
            stepDone(guide.step, now)
            return
        }
        if (seen == null || !guide.isTheItem(seen.view)) {
            // Lost, or something else (the bottle behind it) took its place: look in the middle again.
            trackX = null
            trackY = null
            if (now - lastSeenMs >= LOST_MS && now - lastHintMs >= NO_ITEM_HINT_MS) {
                lastHintMs = now
                services.speaker.say(ItemPhrases.lost(lang))
            }
            return
        }
        lastSeenMs = now
        trackX = seen.mask.anchorX
        trackY = seen.mask.anchorY
        if (now < waitUntilMs || now - lastSampleMs < SAMPLE_GAP_MS) return
        if (!guide.hasMoved(seen.view)) {
            // Still where it was held: repeat the step now and then.
            if (now - lastHintMs >= MOVE_HINT_MS) {
                lastHintMs = now
                guide.step?.let { services.speaker.say(ItemPhrases.prompt(it, lang)) }
            }
            return
        }
        lastSampleMs = now
        lastHintMs = now
        if (photo == null) {
            photo = ItemCrop.rect(seen.square, bitmap.width, bitmap.height)?.let { r ->
                Bitmap.createBitmap(bitmap, r[0], r[1], r[2] - r[0], r[3] - r[1])
            }
        }
        val stepDone = guide.add(seen.view)
        services.haptics.buzz(Buzz.TAP)
        if (stepDone) {
            stepSinceMs = now
            stepDone(guide.step, now)
        } else {
            val percent = guide.percent()
            main.post { _binding?.itemEnrollProgress?.setProgressCompat(percent, true) }
        }
    }

    /** Worker thread: a step ended (done, or left early). Says the [next] one, or saves when there is none. */
    private fun stepDone(next: ItemStep?, now: Long) {
        val percent = guide.percent()
        main.post {
            val b = _binding ?: return@post
            b.itemEnrollProgress.setProgressCompat(percent, true)
            if (next != null) b.itemEnrollPrompt.text = ItemPhrases.prompt(next, lang)
        }
        if (next == null) {
            finish()
            return
        }
        waitUntilMs = now + PROMPT_WAIT_MS
        lastHintMs = waitUntilMs
        // At once, not queued behind a gap: the wait for the user to follow it has already begun.
        services.speaker.sayNow(ItemPhrases.prompt(next, lang))
    }

    /**
     * Worker thread: the item and its samples (the square and the thing alone of each) are written in one
     * transaction on the process-wide scope.
     */
    private fun finish() {
        phase = Phase.FINISHED
        running = false
        val vectors = guide.samples.toList()
        // No detector here, so no kind of thing to show under the name; saved items are found by their look.
        val label = if (kind == ItemKind.CAR) "car" else "object"
        Log.i(TAG, "Item enrolled: ${guide.taken} samples, ${vectors.size} vectors, ${(SystemClock.elapsedRealtime() - openedMs) / 1000} s on the screen")
        val image = photo
        val context = appContext
        val itemName = name
        val itemKind = kind
        AppScope.launch {
            val path = image?.let { PhotoFiles.save(context, PHOTO_FOLDER, it) }
            val dao = AppDatabase.get(context).items()
            // Learning a name again takes the place of what was saved under it: the user was told it was
            // saved and said "replace" (AddItemFragment). Two items of one name stole each other's squares.
            val replaced = dao.allItems().filter { ItemNames.same(it.name, itemName) }
            dao.insertItemWithEmbeddings(
                ItemEntity(
                    name = itemName,
                    kind = itemKind.name,
                    label = label,
                    photoPath = path,
                    createdAt = System.currentTimeMillis(),
                ),
                vectors.map { ItemEmbeddingEntity(vector = VectorBytes.toBytes(it)) },
            )
            replaced.forEach {
                dao.deleteItem(it.id)
                PhotoFiles.delete(it.photoPath)
            }
            if (replaced.isNotEmpty()) Log.i(TAG, "Item replaced ${replaced.size} saved under the same name")
            withContext(Dispatchers.Main) {
                services.haptics.buzz(Buzz.DONE)
                val done = ItemPhrases.done(itemName, lang)
                (services.speaker as? TtsSpeaker)?.brighten(done)
                services.speaker.say(done)
                val tab = if (itemKind == ItemKind.CAR) SavedTab.CARS else SavedTab.OBJECTS
                if (_binding != null) findNavController().returnToSaved(R.id.add_item, tab)
            }
        }
    }

    private fun onCameraError(error: Throwable) {
        Log.i(TAG, "Item enrol camera error", error)
        if (_binding != null) services.speaker.say(getString(R.string.search_camera_error))
    }

    private companion object {
        const val TAG = "Nungil"
        const val PHOTO_FOLDER = "items"

        const val SAMPLE_GAP_MS = 300L
        const val NO_ITEM_HINT_MS = 4_000L
        const val MOVE_HINT_MS = 4_000L

        /** Without the thing in view this long while learning, the user is told. */
        const val LOST_MS = 3_000L

        /**
         * A step (left, right, up) is left with the samples it has after this long. Steps that went well took
         * 4 to 12 s; one that did not took 55 and 98 s (the logs).
         */
        const val STEP_MAX_MS = 20_000L

        /** Time to hear a prompt ("Move the phone a little to the left.") and do it. */
        const val PROMPT_WAIT_MS = 2_000L

        /** Time to hear "Learning (name). Point the camera at it and hold still." and point the phone. */
        const val START_WAIT_MS = 4_000L

        /** How many frames in a row must show a thing before it is described, and how many are agreed on. */
        const val LOOKS_TO_ASK = 3
        const val LOOKS_KEPT = 6

        /** A question without an answer is asked again, about whatever is in view then, after this long. */
        const val ASK_AGAIN_MS = 15_000L

        const val LOG_MS = 2_000L

        /** How strong the tint on the thing is (0..255); its rim is drawn solid. */
        const val MASK_FILL_ALPHA = 0x73
    }
}
