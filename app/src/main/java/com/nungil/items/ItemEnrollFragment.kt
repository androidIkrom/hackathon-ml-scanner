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
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.nungil.R
import com.nungil.contract.Box
import com.nungil.contract.Buzz
import com.nungil.contract.Facing
import com.nungil.contract.ItemKind
import com.nungil.contract.Lang
import com.nungil.contract.SavedTab
import com.nungil.contract.VoiceCommand
import com.nungil.contract.app.AppScope
import com.nungil.contract.app.AppServices
import com.nungil.contract.app.VisionFrame
import com.nungil.contract.app.VoiceHandler
import com.nungil.contract.app.services
import com.nungil.core.items.CenterPick
import com.nungil.core.items.ItemCrop
import com.nungil.core.items.ItemEnrollmentGuide
import com.nungil.core.items.ItemKinds
import com.nungil.core.items.ItemMatcher
import com.nungil.core.items.ItemPhrases
import com.nungil.core.items.ItemStep
import com.nungil.core.people.EnrollPhrases
import com.nungil.core.people.VectorBytes
import com.nungil.data.AppDatabase
import com.nungil.data.ItemEmbeddingEntity
import com.nungil.data.ItemEntity
import com.nungil.databinding.ItemEnrollFragmentBinding
import com.nungil.saved.PhotoFiles
import com.nungil.saved.returnToSaved
import com.nungil.scan.CameraSession
import com.nungil.scan.OverlayView
import com.nungil.search.CameraGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Three-step item enrolment: 4 samples held still, 4 moved left, 4 moved right (12 in all). The box nearest the
 * centre that covers at least 5% of the frame is embedded; detector confidence is 0.3 here. After each prompt the
 * user gets [PROMPT_WAIT_MS] to follow it, and a left or right sample counts only once the item has really shifted
 * (ItemEnrollmentGuide). Nothing is saved until the last sample.
 */
class ItemEnrollFragment : Fragment(), VoiceHandler {
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

    private var camera: CameraSession? = null
    private var extras: ExecutorService? = null
    private val busy = AtomicBoolean(false)

    @Volatile
    private var running = false

    @Volatile
    private var finished = false

    /** No samples before this time, so the user hears the prompt and has time to follow it. */
    @Volatile
    private var waitUntilMs = 0L

    // Extras thread only.
    private var embedder: ItemEmbedder? = null
    private val guide = ItemEnrollmentGuide()
    private val samples = mutableListOf<FloatArray>()
    private val labels = mutableListOf<String>()
    private var photo: Bitmap? = null
    private var lastSampleMs = 0L
    private var lastSeenMs = 0L
    private var lastHintMs = 0L

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
        binding.itemEnrollTitle.text = getString(R.string.item_enroll_title, name)
        ViewCompat.setAccessibilityHeading(binding.itemEnrollTitle, true)
        binding.itemEnrollPrompt.text = ItemPhrases.prompt(ItemStep.STILL, lang)
        binding.itemEnrollButton.setOnClickListener { if (running) pause() else start() }
        gate.attach(binding.itemEnrollPermission)

        val executor = Executors.newSingleThreadExecutor()
        extras = executor
        executor.execute {
            try {
                embedder = ItemEmbedder(appContext)
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
        extras?.let { executor ->
            executor.execute { embedder?.close() }
            executor.shutdown()
            executor.awaitTermination(2, TimeUnit.SECONDS)
        }
        extras = null
        main.removeCallbacksAndMessages(null)
        _binding = null
    }

    override fun onVoiceCommand(command: VoiceCommand): Boolean = when (command) {
        VoiceCommand.Start -> {
            start()
            true
        }
        VoiceCommand.Stop -> {
            pause()
            true
        }
        else -> false
    }

    private fun startCamera() {
        if (camera != null || _binding == null) return
        val options = CameraSession.Options(
            facing = Facing.BACK,
            detect = true,
            keepBitmap = true,
            minScore = ENROLL_MIN_SCORE,
        )
        camera = CameraSession(this, binding.itemEnrollPreview, options, ::onFrame, ::onCameraError).also { it.start() }
    }

    private fun start() {
        if (finished || running) return
        running = true
        waitUntilMs = SystemClock.elapsedRealtime() + PROMPT_WAIT_MS
        binding.itemEnrollButton.setText(R.string.item_enroll_pause)
        services.speaker.say(ItemPhrases.prompt(guide.step ?: ItemStep.STILL, lang))
    }

    private fun pause() {
        if (!running) return
        running = false
        binding.itemEnrollButton.setText(R.string.item_enroll_resume)
        services.speaker.say(EnrollPhrases.paused(lang))
    }

    private fun modelMissing() {
        val b = _binding ?: return
        running = false
        finished = true
        b.itemEnrollButton.isEnabled = false
        b.itemEnrollPrompt.setText(R.string.item_enroll_model_missing)
        services.speaker.say(getString(R.string.item_enroll_model_missing))
        services.haptics.buzz(Buzz.ERROR)
    }

    /** Analysis thread: show the picked box, then hand the frame to the extras thread unless it is busy. */
    private fun onFrame(frame: VisionFrame) {
        val allowed = frame.detections.filter { ItemKinds.allows(kind, it.label) }
        val picked = allowed.getOrNull(CenterPick.pick(allowed))
        val marks = listOfNotNull(picked?.let { OverlayView.Mark(it.box, null, OverlayView.Style.TARGET) })
        main.post { _binding?.itemEnrollOverlay?.show(marks, frame.imageWidth, frame.imageHeight, false) }

        if (!running || finished) return
        val bitmap = frame.bitmap ?: return
        val executor = extras ?: return
        if (!busy.compareAndSet(false, true)) return
        try {
            executor.execute {
                try {
                    process(bitmap, picked?.box, picked?.label)
                } finally {
                    busy.set(false)
                }
            }
        } catch (e: RejectedExecutionException) {
            busy.set(false)
        }
    }

    /** Extras thread. */
    private fun process(bitmap: Bitmap, box: Box?, label: String?) {
        val embedder = embedder ?: return
        if (!running || finished) return
        val now = SystemClock.elapsedRealtime()
        if (box == null || label == null) {
            if (now - lastSeenMs > NO_ITEM_HINT_MS && now - lastHintMs > NO_ITEM_HINT_MS) {
                lastHintMs = now
                services.speaker.say(ItemPhrases.noItem(lang))
            }
            return
        }
        lastSeenMs = now
        if (now < waitUntilMs || now - lastSampleMs < SAMPLE_GAP_MS) return
        if (!guide.accepts(box.centerX)) {
            // Still where it was held: repeat the left/right prompt now and then.
            if (now - lastHintMs > NO_ITEM_HINT_MS) {
                lastHintMs = now
                guide.step?.let { services.speaker.say(ItemPhrases.prompt(it, lang)) }
            }
            return
        }
        val vector = embedder.embed(bitmap, box) ?: return
        lastSampleMs = now
        if (photo == null) {
            photo = ItemCrop.rect(box, bitmap.width, bitmap.height)?.let { r ->
                Bitmap.createBitmap(bitmap, r[0], r[1], r[2] - r[0], r[3] - r[1])
            }
        }
        samples += vector
        labels += label
        val stepDone = guide.add(box.centerX)
        if (stepDone) {
            waitUntilMs = now + PROMPT_WAIT_MS
            lastHintMs = now
        }
        val percent = guide.percent()
        val next = guide.step
        main.post {
            val b = _binding ?: return@post
            b.itemEnrollProgress.setProgressCompat(percent, true)
            if (next != null) b.itemEnrollPrompt.text = ItemPhrases.prompt(next, lang)
        }
        services.haptics.buzz(Buzz.TAP)
        when {
            guide.isDone -> finish()
            stepDone && next != null -> {
                services.speaker.say(EnrollPhrases.percent(percent, lang))
                services.speaker.say(ItemPhrases.prompt(next, lang))
            }
        }
    }

    /** Extras thread: the item and its 12 samples are written in one transaction on the process-wide scope. */
    private fun finish() {
        finished = true
        running = false
        val vectors = samples.toList()
        val label = ItemMatcher.mostCommon(labels) ?: "object"
        val image = photo
        val context = appContext
        val itemName = name
        val itemKind = kind
        AppScope.launch {
            val path = image?.let { PhotoFiles.save(context, PHOTO_FOLDER, it) }
            AppDatabase.get(context).items().insertItemWithEmbeddings(
                ItemEntity(
                    name = itemName,
                    kind = itemKind.name,
                    label = label,
                    photoPath = path,
                    createdAt = System.currentTimeMillis(),
                ),
                vectors.map { ItemEmbeddingEntity(vector = VectorBytes.toBytes(it)) },
            )
            withContext(Dispatchers.Main) {
                services.haptics.buzz(Buzz.DONE)
                services.speaker.say(ItemPhrases.done(itemName, lang))
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

        /** Enrol at confidence 0.3 so the item keeps being found while the phone moves (brief §6). */
        const val ENROLL_MIN_SCORE = 0.3f
        const val SAMPLE_GAP_MS = 300L
        const val NO_ITEM_HINT_MS = 4_000L
        const val PROMPT_WAIT_MS = 2_500L
    }
}
