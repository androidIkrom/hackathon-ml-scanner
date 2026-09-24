package com.nungil.people

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
import com.nungil.contract.Buzz
import com.nungil.contract.Facing
import com.nungil.contract.Lang
import com.nungil.contract.SavedTab
import com.nungil.contract.VoiceCommand
import com.nungil.contract.app.AppScope
import com.nungil.contract.app.AppServices
import com.nungil.contract.app.VisionFrame
import com.nungil.contract.app.VoiceHandler
import com.nungil.contract.app.services
import com.nungil.core.people.EnrollPhrases
import com.nungil.core.people.EnrollmentGuide
import com.nungil.core.people.Pose
import com.nungil.core.people.VectorBytes
import com.nungil.data.AppDatabase
import com.nungil.data.FaceEmbeddingEntity
import com.nungil.data.PersonEntity
import com.nungil.databinding.EnrollFragmentBinding
import com.nungil.saved.PhotoFiles
import com.nungil.saved.returnToSaved
import com.nungil.scan.CameraSession
import com.nungil.search.CameraGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

/**
 * One-sweep face enrolment: 3 samples looking straight, then one each for one side, the other side, up and down
 * (7 in all), taken as soon as the head reaches each pose, with spoken prompts and progress. Nothing is saved until the last sample; leaving halfway saves nothing.
 */
class EnrollFragment : Fragment(), VoiceHandler {
    private var _binding: EnrollFragmentBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<EnrollFragmentArgs>()
    private val gate = CameraGate(this) { startCamera() }
    private val main = Handler(Looper.getMainLooper())

    private lateinit var services: AppServices
    private lateinit var lang: Lang
    private lateinit var name: String
    private lateinit var appContext: Context

    private var camera: CameraSession? = null
    private var extras: ExecutorService? = null
    private val busy = AtomicBoolean(false)

    @Volatile
    private var running = false

    @Volatile
    private var finished = false

    // Extras thread only.
    private var finder: FaceFinder? = null
    private var embedder: FaceEmbedder? = null
    private val guide = EnrollmentGuide()
    private val samples = mutableListOf<FloatArray>()
    private var photo: Bitmap? = null
    private var lastSampleMs = 0L
    private var lastFaceMs = 0L
    private var lastHintMs = 0L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = EnrollFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        services = services()
        lang = services.lang
        name = args.name
        appContext = requireContext().applicationContext
        binding.enrollTitle.text = getString(R.string.enroll_title, name)
        ViewCompat.setAccessibilityHeading(binding.enrollTitle, true)
        binding.enrollPrompt.text = EnrollPhrases.prompt(Pose.STRAIGHT, lang)
        binding.enrollProgress.progress = 0
        binding.enrollButton.setOnClickListener { if (running) pause() else start() }
        gate.attach(binding.enrollPermission)

        val executor = Executors.newSingleThreadExecutor()
        extras = executor
        executor.execute {
            try {
                embedder = FaceEmbedder(appContext)
                finder = FaceFinder()
            } catch (e: Exception) {
                Log.i(TAG, "Face enrolment unavailable", e)
                main.post { modelMissing() }
            }
        }
        services.speaker.say(EnrollPhrases.start(name, lang))
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
            executor.execute {
                finder?.close()
                embedder?.close()
            }
            // No waiting here: blocking the main thread froze the screen while leaving. The close task above
            // still runs last on the extras thread, and shutdown() lets nothing new in.
            executor.shutdown()
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
            facing = if (args.front) Facing.FRONT else Facing.BACK,
            detect = false,
            keepBitmap = true,
        )
        camera = CameraSession(this, binding.enrollPreview, options, ::onFrame, ::onCameraError).also { it.start() }
    }

    private fun start() {
        if (finished || running) return
        running = true
        binding.enrollButton.setText(R.string.enroll_pause)
        val next = guide.pose ?: Pose.STRAIGHT
        services.speaker.say(if (guide.taken == 0) EnrollPhrases.sweep(lang) else EnrollPhrases.prompt(next, lang))
    }

    private fun pause() {
        if (!running) return
        running = false
        binding.enrollButton.setText(R.string.enroll_resume)
        services.speaker.say(EnrollPhrases.paused(lang))
    }

    private fun modelMissing() {
        val b = _binding ?: return
        running = false
        finished = true
        b.enrollButton.isEnabled = false
        b.enrollPrompt.setText(R.string.enroll_model_missing)
        services.speaker.say(getString(R.string.enroll_model_missing))
        services.haptics.buzz(Buzz.ERROR)
    }

    /** Analysis thread: hand the frame to the extras thread unless it is still busy. */
    private fun onFrame(frame: VisionFrame) {
        if (!running || finished) return
        val bitmap = frame.bitmap ?: return
        val executor = extras ?: return
        if (!busy.compareAndSet(false, true)) return
        try {
            executor.execute {
                try {
                    process(bitmap)
                } finally {
                    busy.set(false)
                }
            }
        } catch (e: RejectedExecutionException) {
            busy.set(false)
        }
    }

    /** Extras thread. */
    private fun process(bitmap: Bitmap) {
        val finder = finder ?: return
        val embedder = embedder ?: return
        if (!running || finished) return
        val now = SystemClock.elapsedRealtime()
        val face = finder.find(bitmap).maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
        if (face == null) {
            if (now - lastFaceMs > NO_FACE_HINT_MS && now - lastHintMs > NO_FACE_HINT_MS) {
                lastHintMs = now
                services.speaker.say(EnrollPhrases.noFace(lang))
            }
            return
        }
        lastFaceMs = now
        if (now - lastSampleMs < SAMPLE_GAP_MS) return
        val yaw = face.headEulerAngleY
        val pitch = face.headEulerAngleX
        val box = face.boundingBox
        val hint = guide.hint(min(box.width(), box.height()), yaw, pitch)
        if (hint != null) {
            // Seen but not taken: after a quiet spell, say what to change (and log it for tuning).
            if (now - lastSampleMs > NO_FACE_HINT_MS && now - lastHintMs > NO_FACE_HINT_MS) {
                lastHintMs = now
                Log.i(TAG, "Enrol ${guide.pose}: $hint (yaw=$yaw pitch=$pitch size=${min(box.width(), box.height())})")
                guide.pose?.let { services.speaker.say(EnrollPhrases.hint(hint, it, lang)) }
            }
            return
        }
        val vector = embedder.embed(bitmap, face) ?: return
        lastSampleMs = now
        val before = guide.pose
        val takenPose = guide.add(yaw, pitch) ?: return
        if (photo == null && takenPose == Pose.STRAIGHT) photo = FaceCrops.crop(bitmap, face)
        samples += vector
        val percent = guide.percent()
        val next = guide.pose
        main.post {
            val b = _binding ?: return@post
            b.enrollProgress.setProgressCompat(percent, true)
            if (next != null) b.enrollPrompt.text = EnrollPhrases.prompt(next, lang)
        }
        services.haptics.buzz(Buzz.TAP)
        when {
            guide.isDone -> finish()
            // The head may already be past the next pose: replace any stale prompt instead of queueing.
            next != before && next != null -> services.speaker.sayNow(EnrollPhrases.prompt(next, lang))
        }
    }

    /** Extras thread: everything is written in one transaction on the process-wide scope. */
    private fun finish() {
        finished = true
        running = false
        val vectors = samples.toList()
        val face = photo
        val context = appContext
        val personName = name
        AppScope.launch {
            val path = face?.let { PhotoFiles.save(context, PHOTO_FOLDER, it) }
            AppDatabase.get(context).people().insertPersonWithFaces(
                PersonEntity(name = personName, photoPath = path, createdAt = System.currentTimeMillis()),
                vectors.map { FaceEmbeddingEntity(vector = VectorBytes.toBytes(it)) },
            )
            withContext(Dispatchers.Main) {
                services.haptics.buzz(Buzz.DONE)
                services.speaker.say(EnrollPhrases.done(personName, lang))
                if (_binding != null) findNavController().returnToSaved(R.id.add_person, SavedTab.PEOPLE)
            }
        }
    }

    private fun onCameraError(error: Throwable) {
        Log.i(TAG, "Enrol camera error", error)
        if (_binding != null) services.speaker.say(getString(R.string.search_camera_error))
    }

    private companion object {
        const val TAG = "Nungil"
        const val PHOTO_FOLDER = "people"

        /** At most one sample every 250 ms, so the straight samples differ a little. */
        const val SAMPLE_GAP_MS = 250L
        const val NO_FACE_HINT_MS = 4_000L
    }
}
