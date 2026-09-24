package com.nungil.walk

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import com.nungil.BuildConfig
import com.nungil.R
import com.nungil.contract.Buzz
import com.nungil.contract.Facing
import com.nungil.contract.VoiceCommand
import com.nungil.contract.app.AppServices
import com.nungil.contract.app.VoiceHandler
import com.nungil.contract.app.services
import com.nungil.core.walk.Alert
import com.nungil.core.walk.AlertKind
import com.nungil.core.walk.Announcement
import com.nungil.core.walk.Beacon
import com.nungil.core.walk.GoMath
import com.nungil.core.walk.GoState
import com.nungil.core.walk.LatLon
import com.nungil.core.walk.Navigator
import com.nungil.core.walk.Place
import com.nungil.core.walk.RerouteGate
import com.nungil.core.walk.RoutePhrases
import com.nungil.core.walk.StepLength
import com.nungil.core.walk.WalkAlerts
import com.nungil.core.walk.WalkBeep
import com.nungil.core.walk.WalkCommand
import com.nungil.core.walk.WalkPhrases
import com.nungil.databinding.WalkingFragmentBinding
import com.nungil.scan.CameraSession
import com.nungil.scan.HeadingProvider
import com.nungil.shell.MainActivity
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Walk mode (owner I): obstacle, step and wall warnings from ARCore depth, plus street navigation.
 * Without ARCore it falls back to CameraX detections only. Speech priority: floor change, hazard,
 * ground, traffic light, saved thing, sign, code, navigation, beacon. It never says "safe".
 */
class WalkFragment : Fragment(), VoiceHandler {

    private var _binding: WalkingFragmentBinding? = null
    private val binding get() = _binding!!
    private val main = Handler(Looper.getMainLooper())

    private lateinit var services: AppServices
    private lateinit var vision: WalkVision
    private lateinit var renderer: WalkRenderer
    private lateinit var heading: HeadingProvider
    private lateinit var location: LocationTracker
    private lateinit var places: PlaceStore
    private var stepM: Float? = null

    private var ar: ArCamera? = null
    private var cameraSession: CameraSession? = null
    private var installRequested = false
    private var glRunning = false
    private var running = true
    private var noDepthSaid = false
    private var lastReportAt = 0L
    private var lastBuzzAt = 0L
    private var lastRestartAt = 0L

    // Go mode: search a place, then an arrow and distances on screen while the warnings keep running.
    private var goMode = false
    private lateinit var goPanel: GoPanel
    private val goTicker = object : Runnable {
        override fun run() {
            if (_binding == null || !goMode) return
            updateDirection()
            main.postDelayed(this, GO_TICK_MS)
        }
    }

    /**
     * After a system dialog, ARCore sometimes stops tracking and never recovers on its own (seen on an
     * Infinix X6880 for over a minute). If it has not tracked for [RESTART_AFTER_MS], restart the session.
     */
    private val trackingWatchdog = object : Runnable {
        override fun run() {
            if (_binding == null) return
            val now = SystemClock.elapsedRealtime()
            val stale = now - renderer.lastTrackingAt >= RESTART_AFTER_MS
            if (glRunning && stale && now - lastRestartAt >= RESTART_GAP_MS) {
                lastRestartAt = now
                android.util.Log.i("Nungil", "ARCore not tracking for ${RESTART_AFTER_MS / 1000} s: restarting the session")
                stopCamera()
                startCamera()
            }
            main.postDelayed(this, WATCHDOG_EVERY_MS)
        }
    }

    private val alerts = WalkAlerts()
    private val network: ExecutorService = Executors.newSingleThreadExecutor()
    private val source: RouteSource? = BuildConfig.ORS_API_KEY.takeIf { it.isNotBlank() }?.let { OrsRouteSource(it) }
    private val gate = RerouteGate()
    private var navigator: Navigator? = null
    private var target: Place? = null
    private var pendingNav: Alert? = null
    private var onFirstFix: ((LatLon) -> Unit)? = null
    private var afterLocationGranted: (() -> Unit)? = null

    private val askCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else say(WalkPhrases.needCamera(services.lang))
        askStepsOnce()
    }

    /** Steps since the worker last asked: while depth is lost near a wall, the held distance shrinks per step. */
    private val steps = AtomicInteger(0)
    private var stepsAsked = false
    private val stepListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            steps.addAndGet(event.values.size.coerceAtLeast(1))
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }
    private val askSteps = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startSteps()
    }
    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val then = afterLocationGranted
        afterLocationGranted = null
        if (granted) then?.invoke() else say(WalkPhrases.needLocation(services.lang))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = WalkingFragmentBinding.inflate(inflater, container, false)
        ViewCompat.setAccessibilityHeading(binding.walkingTitle, true)
        binding.walkingGl.apply {
            preserveEGLContextOnPause = true
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        services = services()
        val context = requireContext()
        stepM = if (hasStepDetector(context)) StepLength.metres() else null
        heading = HeadingProvider(context)
        places = PlaceStore(context)
        location = LocationTracker(context) { onFix(it) }
        vision = WalkVision(context, { services.lang }, stepM, { steps.getAndSet(0) }) { onReport(it) }
        renderer = WalkRenderer({ binding.walkingGl.display?.rotation ?: Surface.ROTATION_0 }, vision)
        binding.walkingGl.setRenderer(renderer)
        binding.walkingGl.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        binding.walkingMain.setOnClickListener { setRunning(!running) }
        goPanel = GoPanel(
            binding,
            onSearch = { q -> searchPlaces(q) },
            onMic = { services.askForWords(viewLifecycleOwner) { q -> binding.walkingGoQuery.setText(q); searchPlaces(q) } },
            onPick = { place -> withLocation { here -> requestRoute(here, place, reroute = false) } },
        )
        say(WalkPhrases.started(services.lang))
        (activity as? MainActivity)?.takePendingWalkCommand()?.let { onWalkCommand(it) }
    }

    /**
     * The camera runs from onStart to onStop, not onResume to onPause: a system dialog, a permission
     * prompt or the notification shade over walk mode must not pause ARCore (see ArCamera.pause).
     */
    override fun onStart() {
        super.onStart()
        heading.start()
        if (hasPermission(Manifest.permission.CAMERA)) {
            startCamera()
            askStepsOnce()
        } else {
            askCamera.launch(Manifest.permission.CAMERA)
        }
        startSteps()
        if ((navigator != null || target != null) && hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) location.start()
    }

    override fun onStop() {
        sensorManager().unregisterListener(stepListener)
        stopCamera()
        heading.stop()
        location.stop()
        services.beeper.stop()
        super.onStop()
    }

    override fun onDestroyView() {
        vision.close()
        ar?.close()
        ar = null
        network.shutdownNow()
        main.removeCallbacksAndMessages(null)
        _binding = null
        super.onDestroyView()
    }

    // ---- camera: ARCore, else CameraX -------------------------------------------------------------

    private fun startCamera() {
        if (_binding == null || glRunning || cameraSession != null) return
        val a = ar ?: ArCamera(requireActivity()).also { ar = it }
        when (a.open(userRequestedInstall = !installRequested)) {
            ArCamera.Status.INSTALLING -> {
                installRequested = true
                return
            }
            ArCamera.Status.UNSUPPORTED -> {
                startFallback()
                return
            }
            ArCamera.Status.READY -> Unit
        }
        if (!a.resume()) {
            startFallback()
            return
        }
        renderer.depthEnabled = a.depthSupported
        renderer.semanticsEnabled = a.semanticsSupported
        renderer.lastTrackingAt = SystemClock.elapsedRealtime()
        renderer.session = a.session
        renderer.closing = false
        main.removeCallbacks(trackingWatchdog)
        main.postDelayed(trackingWatchdog, WATCHDOG_EVERY_MS)
        binding.walkingPreview.visibility = View.GONE
        binding.walkingGl.visibility = View.VISIBLE
        binding.walkingGl.onResume()
        glRunning = true
        if (a.depthSupported) {
            status(getString(R.string.walking_status_depth))
        } else {
            noDepth()
        }
    }

    /** Build guide §16: without depth, walk mode keeps the detector hazards and drops wall and step warnings. */
    private fun startFallback() {
        noDepth()
        binding.walkingGl.visibility = View.GONE
        binding.walkingPreview.visibility = View.VISIBLE
        cameraSession = CameraSession(
            fragment = this,
            previewView = binding.walkingPreview,
            options = CameraSession.Options(facing = Facing.BACK, detect = true, keepBitmap = true, minScore = FALLBACK_MIN_SCORE),
            onFrame = { frame -> vision.submitCameraFrame(frame) },
            onError = { },
        ).also { it.start() }
    }

    private fun noDepth() {
        status(getString(R.string.walking_status_no_depth))
        if (!noDepthSaid) {
            noDepthSaid = true
            say(WalkPhrases.noDepth(services.lang))
        }
    }

    /** Teardown order matters: closing flag, free GL objects on the GL thread, pause the GL view, then ARCore. */
    private fun stopCamera() {
        main.removeCallbacks(trackingWatchdog)
        if (glRunning) {
            renderer.closing = true
            binding.walkingGl.queueEvent { renderer.release() }
            binding.walkingGl.onPause()
            renderer.session = null
            ar?.pause()
            glRunning = false
        }
        cameraSession?.stop()
        cameraSession = null
    }

    // ---- what to say ------------------------------------------------------------------------------

    private fun onReport(report: WalkReport) {
        if (_binding == null) return
        lastReportAt = SystemClock.elapsedRealtime()
        if (!running) return
        val now = lastReportAt
        services.beeper.pulse(WalkBeep.intervalMs(report.beepM))
        if (WalkBeep.vibrate(report.beepM) && now - lastBuzzAt >= BUZZ_EVERY_MS) {
            lastBuzzAt = now
            services.haptics.buzz(Buzz.OBSTACLE)
        }
        val candidates = report.alerts + listOfNotNull(pendingNav, beaconAlert())
        alerts.choose(now, candidates)?.let { chosen ->
            android.util.Log.i("Nungil", "Walk said [${chosen.kind} ${chosen.key}] ${chosen.text}")
            if (chosen === pendingNav) pendingNav = null
            if (chosen.urgent) {
                // A step or less from a wall: do not wait behind the queue.
                services.speaker.sayNow(chosen.text)
                _binding?.walkingAnnouncement?.text = chosen.text
            } else {
                say(chosen.text)
            }
        }
    }

    /** Straight-line guidance when a place is set but there is no route (no key, no network, route failed). */
    private fun beaconAlert(): Alert? {
        val place = target ?: return null
        if (navigator != null) return null
        val here = location.last ?: return null
        val metres = Beacon.distanceMetres(here, place.point)
        if (metres <= arrivalRadius()) {
            arrived(place)
            return null
        }
        val h = heading.headingDeg ?: return null
        val clock = Beacon.clock(Beacon.bearingDeg(here, place.point) - h)
        val text = WalkPhrases.beacon(place.name, metres, clock, services.lang)
        return Alert(AlertKind.BEACON, "beacon:$clock:${WalkPhrases.far(metres, services.lang)}", text)
    }

    /** Within 15 m, or within the GPS accuracy when that is worse (indoors it often is). */
    private fun arrivalRadius(): Double = maxOf(Navigator.ARRIVED_M, location.accuracyM.toDouble())

    /** Arrival is always said at once, never queued behind other alerts. */
    private fun arrived(place: Place) {
        navigator = null
        target = null
        pendingNav = null
        location.stop()
        services.haptics.buzz(Buzz.DONE)
        services.speaker.sayNow(RoutePhrases.arrived(place.name, services.lang))
        _binding?.walkingAnnouncement?.text = RoutePhrases.arrived(place.name, services.lang)
        status(getString(R.string.walking_subtitle))
        if (goMode) goPanel.showSearch()
    }

    private fun say(text: String) {
        services.speaker.say(text)
        _binding?.walkingAnnouncement?.text = text
    }

    private fun status(text: String) {
        _binding?.walkingStatus?.text = text
    }

    private fun setRunning(on: Boolean) {
        running = on
        if (_binding == null) return
        binding.walkingMain.setText(if (on) R.string.walking_stop else R.string.walking_start)
        if (on) {
            alerts.reset()
            say(WalkPhrases.started(services.lang))
            status(getString(R.string.walking_subtitle))
        } else {
            services.beeper.stop()
            services.speaker.sayNow(WalkPhrases.stopped(services.lang))
            status(getString(R.string.walking_status_paused))
        }
    }

    // ---- voice ------------------------------------------------------------------------------------

    override fun onVoiceCommand(command: VoiceCommand): Boolean = when (command) {
        VoiceCommand.Stop -> {
            if (navigator != null || target != null) stopNavigation() else if (running) setRunning(false)
            true
        }
        VoiceCommand.Start -> {
            if (!running) setRunning(true)
            true
        }
        else -> false
    }

    /** "save this place as X" / "take me to X", from MainActivity. */
    fun onWalkCommand(command: WalkCommand) {
        if (_binding == null) return
        when (command) {
            WalkCommand.GoMode -> enterGo(null)
            is WalkCommand.SavePlace -> {
                val name = command.name
                if (name == null) {
                    // "save this place" without a name: ask for one, then save.
                    say(WalkPhrases.askPlaceName(services.lang))
                    services.askForWords(viewLifecycleOwner) { answer ->
                        val n = answer.trim().trimEnd('.', '!', '?')
                        if (n.isNotEmpty()) onWalkCommand(WalkCommand.SavePlace(n))
                    }
                } else {
                    withLocation { here ->
                        places.save(Place(name, here))
                        services.haptics.buzz(Buzz.DONE)
                        say(WalkPhrases.placeSaved(name, services.lang))
                    }
                }
            }
            is WalkCommand.GoTo -> {
                enterGo(command.place)
                withLocation { here -> startNavigation(command.place, here) }
            }
        }
    }

    /** Go mode on: title, search panel, and the direction ticker. "take me to X" arrives with [query]. */
    private fun enterGo(query: String?) {
        if (!goMode) {
            goMode = true
            (activity as? MainActivity)?.setScreenTitle(getString(R.string.walking_go_title))
            binding.walkingTitle.setText(R.string.walking_go_title)
            main.removeCallbacks(goTicker)
            main.post(goTicker)
        }
        if (navigator == null && target == null) goPanel.showSearch(query.orEmpty())
        if (query == null) {
            say(RoutePhrases.whereTo(services.lang))
            withLocation { }
        }
    }

    /** Typed or spoken search: saved places first, then nearby results, all with their distance. */
    private fun searchPlaces(query: String) {
        goPanel.showSearching()
        withLocation { here ->
            val lang = services.lang
            val saved = places.all().filter { it.name.contains(query, ignoreCase = true) || query.contains(it.name, ignoreCase = true) }
            val src = source
            if (src == null) {
                goPanel.showResults(saved.map { it to Beacon.distanceMetres(here, it.point) }, lang)
                if (saved.isEmpty()) say(RoutePhrases.notSetUp(lang))
                return@withLocation
            }
            network.execute {
                val found = src.geocode(query, here).take(MAX_CANDIDATES)
                main.post {
                    if (_binding == null) return@post
                    val all = (saved + found).distinctBy { it.name.lowercase() }
                    goPanel.showResults(all.map { it to Beacon.distanceMetres(here, it.point) }, lang)
                    if (all.isEmpty()) say(WalkPhrases.unknownPlace(lang))
                }
            }
        }
    }

    /** Arrow toward the next turn (or the destination), next instruction, distances. */
    private fun updateDirection() {
        val here = location.last ?: return
        val lang = services.lang
        val state: GoState = navigator?.peek(here) ?: target?.let { t ->
            val d = Beacon.distanceMetres(here, t.point).toFloat()
            GoState(t.point, RoutePhrases.headTo(t.name, lang), d, d)
        } ?: return
        goPanel.showDirection(state, heading.headingDeg?.let { GoMath.arrowDeg(here, state.target, it) }, lang)
    }

    private fun withLocation(block: (LatLon) -> Unit) {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            afterLocationGranted = { withLocation(block) }
            askLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        location.start()
        val here = location.last
        if (here != null) {
            block(here)
        } else {
            onFirstFix = block
            say(WalkPhrases.waitingForLocation(services.lang))
        }
    }

    private fun onFix(p: LatLon) {
        onFirstFix?.let {
            onFirstFix = null
            it(p)
        }
        val nav = navigator ?: return
        val announcement = nav.update(SystemClock.elapsedRealtime(), p) ?: return
        when (announcement) {
            is Announcement.Arrived -> {
                target?.let { arrived(it) } ?: run { navigator = null }
                return
            }
            is Announcement.OffRoute -> {
                val place = target
                if (place != null && gate.allow(SystemClock.elapsedRealtime())) requestRoute(p, place, reroute = true)
            }
            else -> Unit
        }
        queueNavigation(announcement.text)
    }

    /** Navigation waits for the next frame so obstacle warnings go first; without frames it speaks at once. */
    private fun queueNavigation(text: String) {
        val alert = Alert(AlertKind.NAVIGATION, "nav:$text", text)
        if (SystemClock.elapsedRealtime() - lastReportAt > REPORT_STALE_MS) say(text) else pendingNav = alert
    }

    private fun startNavigation(name: String, here: LatLon) {
        val lang = services.lang
        val saved = places.find(name)
        if (saved != null && Beacon.distanceMetres(here, saved.point) <= arrivalRadius()) {
            say(WalkPhrases.alreadyAt(saved.name, lang))
            return
        }
        val src = source
        if (src == null) {
            say(RoutePhrases.notSetUp(lang))
            if (saved != null) beaconTo(saved) else say(WalkPhrases.unknownPlace(lang))
            return
        }
        if (saved != null) {
            requestRoute(here, saved, reroute = false)
            return
        }
        network.execute {
            val candidates = src.geocode(name, here).take(MAX_CANDIDATES)
            main.post { if (_binding != null) confirm(candidates, 0, here) }
        }
    }

    /** "Seoul Station, 400 metres away. Say yes to go." "No" moves to the next of up to three candidates. */
    private fun confirm(candidates: List<Place>, index: Int, here: LatLon) {
        val lang = services.lang
        val place = candidates.getOrNull(index) ?: run {
            say(WalkPhrases.unknownPlace(lang))
            return
        }
        if (goMode && index == 0) goPanel.showResults(candidates.map { it to Beacon.distanceMetres(here, it.point) }, lang)
        say(RoutePhrases.confirm(place.name, Beacon.distanceMetres(here, place.point), lang))
        services.askForWords(viewLifecycleOwner) { answer ->
            when {
                RoutePhrases.isYes(answer) -> requestRoute(here, place, reroute = false)
                RoutePhrases.isNo(answer) -> confirm(candidates, index + 1, here)
                else -> say(WalkPhrases.unknownPlace(lang))
            }
        }
    }

    private fun requestRoute(from: LatLon, place: Place, reroute: Boolean) {
        val src = source ?: return
        target = place
        network.execute {
            val result = src.route(from, place)
            main.post { if (_binding != null) onRoute(result, place, reroute) }
        }
    }

    private fun onRoute(result: RouteResult, place: Place, reroute: Boolean) {
        val lang = services.lang
        when (result) {
            is RouteResult.Ok -> {
                navigator = Navigator(result.route, stepM, lang)
                status(getString(R.string.walking_status_navigating, place.name))
                if (!reroute) {
                    say(RoutePhrases.started(place.name, result.route.totalM, lang))
                    say(RoutePhrases.attribution(lang))
                }
            }
            RouteResult.NoRoute -> {
                say(RoutePhrases.noRoute(lang))
                beaconTo(place)
            }
            RouteResult.TooManyRequests -> {
                gate.tooManyRequests(SystemClock.elapsedRealtime())
                say(RoutePhrases.unavailable(lang))
                beaconTo(place)
            }
            RouteResult.Failed -> {
                say(RoutePhrases.unavailable(lang))
                beaconTo(place)
            }
        }
    }

    private fun beaconTo(place: Place) {
        navigator = null
        target = place
        status(getString(R.string.walking_status_beacon, place.name))
    }

    private fun stopNavigation() {
        navigator = null
        target = null
        pendingNav = null
        onFirstFix = null
        location.stop()
        status(getString(R.string.walking_subtitle))
        if (goMode) goPanel.showSearch()
        say(RoutePhrases.navigationStopped(services.lang))
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED

    private fun sensorManager() = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager

    /** Android 10+ needs "physical activity" for the step detector; asked once, after the camera. */
    private fun askStepsOnce() {
        if (stepsAsked || stepM == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        stepsAsked = true
        if (!hasPermission(Manifest.permission.ACTIVITY_RECOGNITION)) askSteps.launch(Manifest.permission.ACTIVITY_RECOGNITION)
    }

    private fun startSteps() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasPermission(Manifest.permission.ACTIVITY_RECOGNITION)) return
        val sensor = sensorManager().getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) ?: return
        sensorManager().unregisterListener(stepListener)
        sensorManager().registerListener(stepListener, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    private fun hasStepDetector(context: Context): Boolean =
        (context.getSystemService(Context.SENSOR_SERVICE) as SensorManager).getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null

    private companion object {
        const val BUZZ_EVERY_MS = 1_000L
        const val REPORT_STALE_MS = 1_000L
        const val MAX_CANDIDATES = 3
        const val FALLBACK_MIN_SCORE = 0.5f
        const val RESTART_AFTER_MS = 5_000L
        const val RESTART_GAP_MS = 15_000L
        const val WATCHDOG_EVERY_MS = 1_000L
        const val GO_TICK_MS = 200L
    }
}
