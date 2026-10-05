package com.nungil.search

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
import androidx.navigation.fragment.navArgs
import com.nungil.R
import com.nungil.contract.Box
import com.nungil.contract.Buzz
import com.nungil.contract.Facing
import com.nungil.contract.Lang
import com.nungil.contract.app.AppServices
import com.nungil.contract.app.VisionFrame
import com.nungil.contract.app.CameraScreen
import com.nungil.contract.app.SwitchableCamera
import com.nungil.contract.app.services
import com.nungil.core.search.SearchGuide
import com.nungil.core.scan.ScanPhrases
import com.nungil.core.search.SearchPhrases
import com.nungil.core.search.SearchTracker
import com.nungil.core.search.TargetType
import com.nungil.databinding.SearchCameraFragmentBinding
import com.nungil.scan.CameraSession
import com.nungil.scan.OverlayView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/**
 * Hunts one target: beeps faster as it nears the centre, vibrates when it enters the centre, names the zone
 * at most every 2 s, and says "Lost it" after 3 s without it.
 */
class SearchCameraFragment : Fragment(), CameraScreen {
    private var _binding: SearchCameraFragmentBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<SearchCameraFragmentArgs>()
    private val gate = CameraGate(this) { startCamera() }
    private val main = Handler(Looper.getMainLooper())

    private lateinit var services: AppServices
    private lateinit var lang: Lang
    private lateinit var spokenName: String
    private lateinit var type: TargetType

    private var camera: CameraSession? = null
    private var extras: ExecutorService? = null
    private val busy = AtomicBoolean(false)
    private val matcher = AtomicReference<TargetMatcher?>(null)
    private val tracker = SearchTracker()

    @Volatile
    private var lastPulseMs = -1L

    @Volatile
    private var leaving = false

    /** "Stop" paused the search: frames are not matched and the beeps are off; one more "stop" leaves. */
    @Volatile
    private var paused = false

    @Volatile
    private var last: VisionFrame? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SearchCameraFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        leaving = false
        paused = false
        services = services()
        lang = services.lang
        spokenName = args.spokenName
        type = TargetType.entries.firstOrNull { it.name == args.targetType } ?: TargetType.LABEL
        binding.searchCameraTitle.text = getString(R.string.search_camera_title, spokenName)
        ViewCompat.setAccessibilityHeading(binding.searchCameraTitle, true)
        binding.searchCameraStop.setOnClickListener { services.navigator.back() }
        gate.attach(binding.searchCameraPermission)

        val executor = Executors.newSingleThreadExecutor()
        extras = executor
        val context = requireContext().applicationContext
        val targetId = args.targetId
        val label = args.targetLabel
        executor.execute {
            val created = try {
                TargetMatchers.create(context, type, targetId, label)
            } catch (e: Exception) {
                if (type == TargetType.ITEM) {
                    // A saved item is found by its look alone, with the detector off: no model, no search.
                    Log.i(TAG, "Item search unavailable", e)
                    services.speaker.say(context.getString(R.string.item_enroll_model_missing))
                    return@execute
                }
                Log.i(TAG, "Search matcher failed, hunting by label instead", e)
                LabelMatcher(if (type == TargetType.PERSON) "person" else label)
            }
            matcher.set(created)
        }
        services.speaker.sayNow(SearchPhrases.looking(spokenName, lang))
    }

    override fun onResume() {
        super.onResume()
        gate.check()
    }

    override fun onPause() {
        super.onPause()
        services.beeper.stop()
        lastPulseMs = -1L
    }

    override fun onDestroyView() {
        super.onDestroyView()
        leaving = true
        camera?.stop()
        camera = null
        services.beeper.stop()
        gate.detach()
        extras?.let { executor ->
            // Take the matcher now, so a close that runs late cannot close the next view's matcher.
            val old = matcher.getAndSet(null)
            executor.execute { old?.close() }
            // No waiting here: blocking the main thread froze the screen while leaving. The close task above
            // still runs last on the extras thread, and shutdown() lets nothing new in.
            executor.shutdown()
        }
        extras = null
        main.removeCallbacksAndMessages(null)
        _binding = null
    }

    override fun lastFrame(): VisionFrame? = last

    override val switchable: SwitchableCamera? get() = camera

    override val isWorking: Boolean get() = !paused

    /** "Stop" used to leave Find at once; now it pauses, as on the other camera screens, and a second one leaves. */
    override fun pause() {
        if (_binding == null || paused) return
        paused = true
        services.beeper.stop()
        lastPulseMs = -1L
        services.speaker.sayNow(ScanPhrases.paused(lang))
    }

    override fun resume() {
        if (_binding == null || !paused) return
        paused = false
        services.speaker.sayNow(SearchPhrases.looking(spokenName, lang))
    }

    private fun startCamera() {
        if (camera != null || _binding == null) return
        val options = CameraSession.Options(
            facing = Facing.BACK,
            // A saved item is looked for in the picture itself (ItemFinder); the detector would only slow it.
            detect = type != TargetType.ITEM,
            // Every target keeps the picture: "who is this" and "what is this" answer from it.
            keepBitmap = true,
            minScore = SEARCH_MIN_SCORE,
        )
        camera = CameraSession(this, binding.searchCameraPreview, options, ::onFrame, ::onCameraError).also { it.start() }
    }

    /** Analysis thread. Fast matchers run here; slow ones go to the extras thread, dropping frames while busy. */
    private fun onFrame(frame: VisionFrame) {
        last = frame
        if (paused) return
        val m = matcher.get() ?: return
        if (!m.slow) {
            handle(frame, m.locate(frame))
            return
        }
        val executor = extras ?: return
        if (!busy.compareAndSet(false, true)) return
        try {
            executor.execute {
                try {
                    handle(frame, safeLocate(m, frame))
                } finally {
                    busy.set(false)
                }
            }
        } catch (e: RejectedExecutionException) {
            busy.set(false)
        }
    }

    private fun safeLocate(m: TargetMatcher, frame: VisionFrame): Box? = try {
        m.locate(frame)
    } catch (e: Exception) {
        Log.i(TAG, "Search matcher error", e)
        null
    }

    /** Worker thread: speech, beeps and vibration are thread-safe; the overlay is updated on the main thread. */
    private fun handle(frame: VisionFrame, target: Box?) {
        // A frame still in flight after the screen was left or paused must not restart the beeps or speak.
        if (leaving || paused) return
        val x = target?.let { SearchGuide.userX(it.centerX, frame.facing) }
        val zone = x?.let { SearchGuide.zone(it) }
        val update = synchronized(tracker) { tracker.update(SystemClock.elapsedRealtime(), zone) }

        val pulse = x?.let { SearchGuide.beepIntervalMs(it) } ?: 0L
        if (pulse == 0L && lastPulseMs != 0L || pulse > 0L && abs(pulse - lastPulseMs) >= PULSE_STEP_MS) {
            lastPulseMs = pulse
            services.beeper.pulse(pulse)
        }
        if (update.enteredCenter) services.haptics.buzz(Buzz.CENTERED)
        when (val say = update.say) {
            is SearchTracker.Say.Where -> services.speaker.say(SearchPhrases.where(spokenName, say.zone, lang))
            SearchTracker.Say.Lost -> services.speaker.say(SearchPhrases.lost(lang))
            null -> Unit
        }

        val marks = frame.detections.filter { it.box != target }.map { OverlayView.Mark(it.box, null, OverlayView.Style.DIM) } +
            listOfNotNull(target?.let { OverlayView.Mark(it, spokenName, OverlayView.Style.TARGET) })
        main.post {
            val b = _binding ?: return@post
            b.searchCameraOverlay.show(marks, frame.imageWidth, frame.imageHeight, frame.facing == Facing.FRONT)
            b.searchCameraStatus.text = zone?.let { SearchGuide.zoneWord(it, lang) } ?: getString(R.string.search_camera_looking)
        }
    }

    private fun onCameraError(error: Throwable) {
        Log.i(TAG, "Search camera error", error)
        if (_binding != null) services.speaker.say(getString(R.string.search_camera_error))
    }

    private companion object {
        const val TAG = "Nungil"

        /** The search detector runs at confidence 0.4 (brief §6). */
        const val SEARCH_MIN_SCORE = 0.4f

        /** Only re-arm the beeper when the interval moved by at least this much. */
        const val PULSE_STEP_MS = 25L
    }
}
