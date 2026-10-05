package com.nungil.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.color.MaterialColors
import com.nungil.R
import com.nungil.contract.Box
import com.nungil.contract.Buzz
import com.nungil.contract.Facing
import com.nungil.contract.Lang
import com.nungil.contract.ScanMode
import com.nungil.contract.ScanSettings
import com.nungil.contract.app.AppScope
import com.nungil.contract.app.NameTagger
import com.nungil.contract.app.TagKind
import com.nungil.contract.app.VisionFrame
import com.nungil.contract.app.CameraScreen
import com.nungil.contract.app.SwitchableCamera
import com.nungil.contract.app.services
import com.nungil.core.items.Reach
import com.nungil.core.lang.LabelNames
import com.nungil.core.scan.BoxGeometry
import com.nungil.core.scan.ColorPolicy
import com.nungil.core.scan.FoundPlaces
import com.nungil.core.scan.ScanLogState
import com.nungil.core.scan.ScanPhrases
import com.nungil.core.scan.ScanResult
import com.nungil.core.scan.ScanSession
import com.nungil.core.scan.StickyNames
import com.nungil.data.AppDatabase
import com.nungil.data.SettingsStore
import com.nungil.databinding.ScanFragmentBinding
import com.nungil.databinding.ScanLogSheetBinding
import com.nungil.people.createNameTaggers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Full and live scan. Nav argument `mode: ScanMode`.
 * Threads: camera frames arrive on CameraSession's analysis thread; faces, items and the scene classifier
 * run on [extras] (one at a time, busy flag); everything touching views or speech runs on the main thread.
 */
class ScanFragment : Fragment(), CameraScreen {

    private enum class State { NO_PERMISSION, IDLE, SCANNING }

    /** [byLook]: a saved item found where the detector drew no box (FoundPlaces), [box] being its place. */
    private class PendingTag(val box: Box, val name: String, val isPerson: Boolean, val byLook: Boolean = false)

    private var _binding: ScanFragmentBinding? = null
    private val binding get() = _binding!!
    private val args: ScanFragmentArgs by navArgs()
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var settings = ScanSettings()
    private var state = State.IDLE
    private var camera: CameraSession? = null
    private var autoStart: Runnable? = null
    private var cameraErrorSpoken = false
    private val log = ScanLogState()

    @Volatile private var lang = Lang.EN
    @Volatile private var extras: ExecutorService? = null
    private val extrasBusy = AtomicBoolean(false)
    @Volatile private var taggers: List<NameTagger> = emptyList()
    @Volatile private var classifier: SceneClassifier? = null
    private val pendingTags = ConcurrentLinkedQueue<PendingTag>()

    // Analysis thread only.
    private val sticky = StickyNames()
    private val places = FoundPlaces()

    // Written on the analysis thread, read for "what / who is this" (FrameAnswers) and the empty-scan guess.
    @Volatile private var lastFrame: VisionFrame? = null

    private val sessionLock = Any()
    private var session: ScanSession? = null

    private val askCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (_binding == null) return@registerForActivityResult
        if (granted) startCamera() else showNoPermission()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ScanFragmentBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settings = SettingsStore(requireContext()).load()
        lang = services().lang
        val full = args.mode == ScanMode.FULL
        binding.scanTitle.setText(if (full) R.string.scan_title_full else R.string.scan_title_live)
        binding.scanSubtitle.setText(if (full) R.string.scan_subtitle_full else R.string.scan_subtitle_live)
        ViewCompat.setAccessibilityHeading(binding.scanTitle, true)
        binding.scanRing.isVisible = full
        binding.scanMainButton.setOnClickListener { onMainButton() }
        binding.scanViewText.setOnClickListener { showLog() }
        binding.scanSwitchCamera.setOnClickListener { switchCamera() }
        binding.scanOpenSettings.setOnClickListener { openAppSettings() }
        extras = Executors.newSingleThreadExecutor()
        if (hasCameraPermission()) startCamera() else askCamera.launch(Manifest.permission.CAMERA)
    }

    override fun onResume() {
        super.onResume()
        // Coming back from the system settings with the permission granted.
        if (_binding != null && camera == null && hasCameraPermission()) startCamera()
    }

    override fun onDestroyView() {
        autoStart?.let(main::removeCallbacks)
        synchronized(sessionLock) { session = null }
        camera?.stop()
        camera = null
        val ex = extras
        extras = null
        if (ex != null) {
            val toClose = taggers
            val cls = classifier
            ex.execute {
                toClose.forEach { runCatching { it.close() } }
                runCatching { cls?.close() }
            }
            ex.shutdown()
            try {
                if (!ex.awaitTermination(2, TimeUnit.SECONDS)) ex.shutdownNow()
            } catch (e: InterruptedException) {
                ex.shutdownNow()
            }
        }
        taggers = emptyList()
        classifier = null
        main.removeCallbacksAndMessages(null)
        _binding = null
        super.onDestroyView()
    }

    override fun lastFrame(): VisionFrame? = lastFrame

    override val switchable: SwitchableCamera? get() = camera

    /** Scanning, or the scan about to start on its own: "stop" right after opening must cancel that start. */
    override val isWorking: Boolean get() = state == State.SCANNING || autoStart != null

    override fun pause() {
        if (_binding == null) return
        if (state == State.SCANNING) {
            stopScan()
        } else if (state == State.IDLE && autoStart != null) {
            autoStart?.let(main::removeCallbacks)
            autoStart = null
            speakNow(ScanPhrases.stopped(lang))
        }
    }

    override fun resume() {
        if (_binding == null) return
        if (state == State.NO_PERMISSION) speakNow(ScanPhrases.cameraNeeded(lang)) else if (state == State.IDLE) startScan()
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        if (camera != null || _binding == null) return
        binding.scanPermissionPanel.isVisible = false
        val app = requireContext().applicationContext
        extras?.execute {
            taggers = try {
                createNameTaggers(app, Reach.WHOLE_FRAME)
            } catch (t: Throwable) {
                Log.i(TAG, "Name taggers unavailable: ${t.message}")
                emptyList()
            }
        }
        camera = CameraSession(
            this,
            binding.scanPreview,
            CameraSession.Options(facing = settings.facing, keepBitmap = true),
            ::onFrame,
            ::onCameraError,
        ).also { it.start() }
        showState(State.IDLE)
        speak(ScanPhrases.intro(args.mode, lang))
        autoStart = Runnable {
            autoStart = null
            if (_binding != null && state == State.IDLE) startScan()
        }
            .also { main.postDelayed(it, AUTO_START_MS) }
    }

    private fun onMainButton() = when (state) {
        State.NO_PERMISSION -> askCamera.launch(Manifest.permission.CAMERA)
        State.IDLE -> startScan()
        State.SCANNING -> stopScan()
    }

    private fun startScan() {
        autoStart?.let(main::removeCallbacks)
        autoStart = null
        if (camera == null) return
        synchronized(sessionLock) {
            val app = services()
            session = ScanSession(args.mode, System.currentTimeMillis(), lang, { app.directionStyle }, settings.colorsOn)
        }
        showState(State.SCANNING)
    }

    private fun stopScan() {
        val result = synchronized(sessionLock) {
            val s = session ?: return
            session = null
            s.finish()
        }
        showState(State.IDLE)
        services().haptics.buzz(Buzz.DONE)
        if (args.mode == ScanMode.FULL) {
            log.add(result.summary)
            if (settings.speechOn) services().speaker.sayFinal(result.summary)
            if (result.objects.isEmpty()) guessWhenEmpty()
        } else {
            speakNow(ScanPhrases.stopped(lang))
        }
        save(result)
    }

    private fun save(result: ScanResult) {
        val db = AppDatabase.get(requireContext())
        val scan = ScanRecords.scanEntity(result, lang)
        val objects = ScanRecords.objectEntities(result)
        AppScope.launch { db.scans().insertScanWithObjects(scan, objects) }
    }

    /** Analysis thread. */
    private fun onFrame(frame: VisionFrame) {
        lastFrame = frame
        while (true) {
            val tag = pendingTags.poll() ?: break
            if (tag.byLook) places.found(tag.box, tag.name, frame.timestampMs) else sticky.recognized(tag.box, tag.name, tag.isPerson)
        }
        val usable = frame.detections.filterNot { BoxGeometry.touchesOneSideEdge(it.box) }
        val names = sticky.apply(usable.map { it.box })
        // Saved items found by their look, not already the name of one of this frame's boxes.
        val found = places.current(frame.timestampMs).filter { p -> names.none { it?.name == p.name } }
        val scanning = synchronized(sessionLock) { session != null }
        var step: ScanSession.Step? = null
        if (scanning) {
            val bitmap = frame.bitmap
            val light = if (bitmap != null && settings.colorsOn) ColorSampler.frameLight(bitmap) else null
            val seen = usable.mapIndexed { i, d ->
                val name = names[i]
                val color = if (name == null && bitmap != null && light != null && ColorPolicy.hasColor(d.label)) {
                    ColorSampler.colorOf(bitmap, d.box, light)
                } else {
                    null
                }
                ScanSession.Seen(name?.name ?: d.label, d.box.centerX, color, isName = name != null, wasPerson = d.label == "person")
            } + found.map { ScanSession.Seen(it.name, it.box.centerX, null, isName = true) }
            step = synchronized(sessionLock) {
                session?.onFrame(frame.timestampMs, frame.headingDeg, frame.hfovDeg, frame.facing, seen)
            }
        }
        val marks = usable.mapIndexed { i, d -> OverlayView.Mark(d.box, names[i]?.name ?: LabelNames.name(d.label, lang)) } +
            found.map { OverlayView.Mark(it.box, it.name) }
        main.post { render(frame, marks, step) }
        runTaggers(frame)
    }

    /** Main thread. */
    private fun render(frame: VisionFrame, marks: List<OverlayView.Mark>, step: ScanSession.Step?) {
        val b = _binding ?: return
        b.scanOverlay.show(marks, frame.imageWidth, frame.imageHeight, frame.facing == Facing.FRONT)
        if (step == null || state != State.SCANNING) return
        if (args.mode == ScanMode.FULL) b.scanRing.setCoverage(step.coveragePercent, step.bins)
        step.phrases.forEach { speak(it) }
        step.news.forEach { speakLive(it) }
        if (step.done) stopScan()
    }

    /** Analysis thread: hand the frame to the name taggers when the extras thread is free. */
    private fun runTaggers(frame: VisionFrame) {
        val ex = extras ?: return
        if (frame.bitmap == null || taggers.isEmpty()) return
        if (!extrasBusy.compareAndSet(false, true)) return
        try {
            ex.execute {
                try {
                    for (tagger in taggers) {
                        for (tag in tagger.tag(frame)) {
                            val place = tag.box
                            if (place != null) {
                                pendingTags.add(PendingTag(place, tag.name, isPerson = false, byLook = true))
                                continue
                            }
                            val d = frame.detections.getOrNull(tag.detectionIndex) ?: continue
                            pendingTags.add(PendingTag(d.box, tag.name, tag.kind == TagKind.PERSON))
                        }
                    }
                } catch (t: Throwable) {
                    Log.i(TAG, "Name tagger failed: ${t.message}")
                } finally {
                    extrasBusy.set(false)
                }
            }
        } catch (e: RejectedExecutionException) {
            extrasBusy.set(false)
        }
    }

    /** When a full scan finds nothing, the 1000-class classifier names the middle of the frame. */
    private fun guessWhenEmpty() {
        classifyCenter { guess -> if (guess != null) speak(ScanPhrases.looksLike(guess, lang)) }
    }

    private fun classifyCenter(onResult: (String?) -> Unit) {
        val bitmap = lastFrame?.bitmap
        val ex = extras
        if (bitmap == null || ex == null) {
            onResult(null)
            return
        }
        val app = requireContext().applicationContext
        try {
            ex.execute {
                val guess = try {
                    (classifier ?: SceneClassifier(app).also { classifier = it }).nameCenter(bitmap)
                } catch (t: Throwable) {
                    Log.i(TAG, "Scene classifier failed: ${t.message}")
                    null
                }
                main.post { if (_binding != null) onResult(guess) }
            }
        } catch (e: RejectedExecutionException) {
            onResult(null)
        }
    }

    private fun switchCamera(to: Facing? = null) {
        val c = camera ?: return
        c.useCamera(to)
        speakNow(ScanPhrases.cameraSwitched(c.facing, lang))
    }

    private fun onCameraError(error: Throwable) {
        Log.i(TAG, "Camera error: ${error.message}")
        if (cameraErrorSpoken || _binding == null) return
        cameraErrorSpoken = true
        services().haptics.buzz(Buzz.ERROR)
        speakNow(ScanPhrases.cameraProblem(lang))
    }

    private fun showNoPermission() {
        showState(State.NO_PERMISSION)
        binding.scanPermissionPanel.isVisible = true
        speakNow(ScanPhrases.cameraNeeded(lang))
    }

    private fun showState(newState: State) {
        state = newState
        val b = _binding ?: return
        val button = b.scanMainButton
        val (text, fill, onFill) = when (newState) {
            State.NO_PERMISSION -> Triple(R.string.scan_permission_allow, R.attr.ngPrimary, R.attr.ngOnPrimary)
            State.IDLE -> Triple(R.string.scan_start, R.attr.ngPrimary, R.attr.ngOnPrimary)
            State.SCANNING -> Triple(R.string.scan_stop, R.attr.ngDanger, R.attr.ngOnDanger)
        }
        button.setText(text)
        button.backgroundTintList = ColorStateList.valueOf(MaterialColors.getColor(button, fill))
        button.setTextColor(MaterialColors.getColor(button, onFill))
        b.scanSwitchCamera.isEnabled = newState != State.NO_PERMISSION
    }

    private fun showLog() {
        val sheet = BottomSheetDialog(requireContext())
        val sheetBinding = ScanLogSheetBinding.inflate(layoutInflater)
        sheetBinding.scanLogText.text = log.text().ifEmpty { getString(R.string.scan_log_empty) }
        sheet.setContentView(sheetBinding.root)
        sheet.show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", requireContext().packageName, null))
        startActivity(intent)
    }

    private fun speak(text: String) {
        log.add(text)
        if (settings.speechOn) services().speaker.say(text)
    }

    /** A thing just confirmed: said soon, together with the others still waiting (Speaker.sayLive). */
    private fun speakLive(text: String) {
        log.add(text)
        Log.i(TAG, "Live: \"$text\"")
        if (settings.speechOn) services().speaker.sayLive(text)
    }

    private fun speakNow(text: String) {
        log.add(text)
        if (settings.speechOn) services().speaker.sayNow(text)
    }

    private companion object {
        const val TAG = "Nungil"

        /** Let the spoken introduction start before the scan begins. */
        const val AUTO_START_MS = 1_500L
    }
}
