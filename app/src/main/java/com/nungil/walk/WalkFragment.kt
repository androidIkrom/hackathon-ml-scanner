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
import android.util.Log
import android.view.LayoutInflater
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import com.google.ar.core.TrackingFailureReason
import com.nungil.BuildConfig
import com.nungil.R
import com.nungil.contract.Buzz
import com.nungil.contract.Facing
import com.nungil.contract.VoiceCommand
import com.nungil.contract.app.AppServices
import com.nungil.contract.app.CameraScreen
import com.nungil.contract.app.SwitchableCamera
import com.nungil.contract.app.VisionFrame
import com.nungil.contract.app.VoiceHandler
import com.nungil.contract.app.services
import com.nungil.core.walk.Alert
import com.nungil.core.walk.AlertKind
import com.nungil.core.walk.Announcement
import com.nungil.core.walk.Beacon
import com.nungil.core.walk.GoApproach
import com.nungil.core.walk.GoMath
import com.nungil.core.walk.GoPace
import com.nungil.core.walk.GoProgress
import com.nungil.core.walk.GoQuestion
import com.nungil.core.walk.GoAnswer
import com.nungil.core.walk.GoState
import com.nungil.core.walk.FixAge
import com.nungil.core.walk.GpsSignal
import com.nungil.core.walk.LatLon
import com.nungil.core.walk.Navigator
import com.nungil.core.walk.Place
import com.nungil.core.walk.PlaceNames
import com.nungil.core.walk.RerouteGate
import com.nungil.core.walk.RoutePhrases
import com.nungil.core.walk.StepLength
import com.nungil.core.walk.TrackingRestart
import com.nungil.core.ui.Announcer
import com.nungil.core.walk.toNotice
import com.nungil.core.walk.WalkBeep
import com.nungil.core.walk.WalkCommand
import com.nungil.core.walk.WalkPhrases
import com.nungil.databinding.WalkingFragmentBinding
import com.nungil.design.resolveColorAttr
import com.nungil.scan.CameraSession
import com.nungil.scan.HeadingProvider
import com.nungil.shell.MainActivity
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Walk mode (owner I): obstacle, step and wall warnings from ARCore depth, plus street navigation.
 * Without ARCore it falls back to CameraX detections only. Speech priority: floor change, hazard,
 * ground, traffic light, saved thing, sign, code, navigation, beacon. It never says "safe".
 */
class WalkFragment : Fragment(), VoiceHandler, CameraScreen {

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
    private val restart = TrackingRestart()
    private var cameraStartedAt = 0L

    // Go mode: search a place, then an arrow and distances on screen while the warnings keep running.
    private var goMode = false
    private lateinit var goPanel: GoPanel
    private val goTicker = object : Runnable {
        override fun run() {
            if (_binding == null || !goMode) return
            updateDirection()
            goUpdate()
            goDirection()
            main.postDelayed(this, GO_TICK_MS)
        }
    }

    /**
     * After a system dialog, ARCore sometimes stops tracking and never recovers on its own (seen on an
     * Infinix X6880 for over a minute). Then restart the session, but only as [TrackingRestart] allows:
     * restarting in the dark crashed ARCore natively.
     */
    private val trackingWatchdog = object : Runnable {
        override fun run() {
            if (_binding == null) return
            val now = SystemClock.elapsedRealtime()
            val reason = renderer.failureReason
            val selfRecovers = reason == TrackingFailureReason.INSUFFICIENT_LIGHT ||
                reason == TrackingFailureReason.INSUFFICIENT_FEATURES ||
                reason == TrackingFailureReason.EXCESSIVE_MOTION
            // The Go search step hides the camera: no frames, nothing to restart.
            val visible = binding.walkingCameraCard.isShown
            if (!visible) cameraStartedAt = now
            if (glRunning && visible && restart.shouldRestart(now, renderer.lastTrackingAt, cameraStartedAt, selfRecovers)) {
                android.util.Log.i("Nungil", "ARCore not tracking ($reason): restarting the session")
                stopCamera()
                startCamera()
            }
            main.postDelayed(this, WATCHDOG_EVERY_MS)
        }
    }

    private val announcer = Announcer(Announcer.WALK)
    private var network: ExecutorService = Executors.newSingleThreadExecutor()
    private val source: RouteSource? = BuildConfig.ORS_API_KEY.takeIf { it.isNotBlank() }?.let { OrsRouteSource(it) }
    private val gate = RerouteGate()
    private var navigator: Navigator? = null
    private var target: Place? = null
    private var pendingNav: Alert? = null

    // Go progress: the minute update, the walker's speed, questions, weak GPS (spec 2026-10-04-go-progress).
    private lateinit var goSettings: GoSettings
    private lateinit var progress: GoProgress

    /** The direction every 10 s (goDirection). */
    private val direction = GoProgress(everyMs = DIRECTION_EVERY_MS)
    private val pace = GoPace()
    private val gps = GpsSignal()
    private var beaconApproach = GoApproach()
    private var beaconStartM: Float? = null
    private var rerouting = false

    /** The last navigation sentence, for "repeat", and when it was queued (the minute update waits after one). */
    private var lastNav: String? = null
    private var lastNavAt = Long.MIN_VALUE / 2

    /** Why the minute update last waited: logged when it changes, not five times a second. */
    private var lastPutOff: GoProgress.Verdict? = null

    /** A found place that was read out and waits for "yes", "next", "the second one" or another place. */
    private class Offer(val candidates: List<Place>, val index: Int, val here: LatLon, var askedAgain: Boolean = false)
    private var offer: Offer? = null
    private var searches = 0
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
        goSettings = GoSettings(context)
        // Back from another screen this is the same fragment: its route, timer and network thread carry on
        // (onDestroyView shut the thread down, and "where am I" crashed on it, the review).
        if (!::progress.isInitialized) progress = GoProgress(goSettings.quietUpdates)
        if (network.isShutdown) network = Executors.newSingleThreadExecutor()
        location = LocationTracker(context) { onFix(it) }
        vision = WalkVision(context, { services.lang }, { services.directionStyle }, stepM, { steps.getAndSet(0) }, { heading.headingDeg }) { onReport(it) }
        renderer = WalkRenderer({ binding.walkingGl.display?.rotation ?: Surface.ROTATION_0 }, vision)
        binding.walkingGl.setRenderer(renderer)
        binding.walkingGl.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        binding.walkingMain.setOnClickListener {
            when {
                goMode && !navigating() -> goPanel.query().takeIf { it.isNotEmpty() }?.let { searchPlaces(it) }
                goMode -> stopNavigation()
                else -> setRunning(!running)
            }
        }
        // Go mode: back from the route returns to the search, not to Home.
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, goBack)
        goPanel = GoPanel(
            binding,
            onSearch = { q -> searchPlaces(q) },
            onMic = { services.askForWords(viewLifecycleOwner) { q -> binding.walkingGoQuery.setText(q); searchPlaces(q) } },
            onPick = { place ->
                offer = null
                (activity as? MainActivity)?.dropWords()
                withLocation { here -> requestRoute(here, place, reroute = false) }
            },
        )
        // Opened for Go mode ("take me to X", the Go card): say "Where to?" there, not the walk intro.
        val pending = (activity as? MainActivity)?.takePendingWalkCommand()
        if (pending !is WalkCommand.GoMode && pending !is WalkCommand.GoTo) say(WalkPhrases.started(services.lang))
        pending?.let { onWalkCommand(it) }
        if (goMode) {
            main.removeCallbacks(goTicker)
            main.post(goTicker)
        }
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
        cameraStartedAt = SystemClock.elapsedRealtime()
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
        beaconArrival()
        val candidates = report.alerts + listOfNotNull(pendingNav)
        announcer.choose(now, candidates.map { it.toNotice() })?.let { chosen ->
            val kind = candidates.firstOrNull { it.key == chosen.key }?.kind
            android.util.Log.i("Nungil", "Walk said [$kind ${chosen.key}] ${chosen.text}")
            if (chosen.key == pendingNav?.key) pendingNav = null
            // Never queued (see Announcer): the newest sentence replaces the one being said.
            services.speaker.sayNow(chosen.text)
            _binding?.walkingAnnouncement?.text = chosen.text
        }
    }

    /**
     * Arrival when a place is set but there is no route (no key, no network, route failed). Its direction is
     * the one said every 10 s (goDirection): the sentence said at every change of the clock came nine times in
     * 50 s while the walker turned (the logs).
     */
    private fun beaconArrival() {
        val place = target ?: return
        if (navigator != null) return
        val here = location.last ?: return
        if (Beacon.distanceMetres(here, place.point) <= arrivalRadius()) arrived(place)
    }

    /** Within 15 m, or within the GPS accuracy when that is worse (indoors it often is). */
    private fun arrivalRadius(): Double = maxOf(Navigator.ARRIVED_M, location.accuracyM.toDouble())

    /** Arrival is always said at once, never queued behind other alerts, with the side the place is on. */
    private fun arrived(place: Place) {
        val text = RoutePhrases.arrived(place.name, services.lang, navigator?.side)
        navigator = null
        target = null
        pendingNav = null
        lastNav = null
        progress.stop()
        direction.stop()
        location.stop()
        services.haptics.buzz(Buzz.DONE)
        services.speaker.sayNow(text)
        _binding?.walkingAnnouncement?.text = text
        status(getString(R.string.walking_subtitle))
        if (goMode) showGoSearch()
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
            announcer.reset()
            say(WalkPhrases.started(services.lang))
            status(getString(R.string.walking_subtitle))
        } else {
            services.beeper.stop()
            services.speaker.sayNow(WalkPhrases.stopped(services.lang))
            status(getString(R.string.walking_status_paused))
        }
    }

    // ---- voice ------------------------------------------------------------------------------------

    override fun lastFrame(): VisionFrame? = if (::vision.isInitialized) vision.lastFrame() else null

    /** ARCore owns the camera here: the back camera only. */
    override val switchable: SwitchableCamera? get() = null

    override val backCameraOnly: Boolean get() = true

    override val isWorking: Boolean get() = navigator != null || target != null || offer != null || running

    override fun pause() {
        if (_binding == null) return
        if (navigator != null || target != null || offer != null) stopNavigation() else if (running) setRunning(false)
    }

    override fun resume() {
        if (_binding == null) return
        val asked = offer
        if (asked != null) {
            // "go" and "start" are commands, so they never reach the answer: here they mean yes.
            onGoAnswer(asked, GoAnswer.Yes)
        } else if (onGoSearch()) {
            val q = goPanel.query()
            if (q.isEmpty()) say(RoutePhrases.whereTo(services.lang)) else searchPlaces(q)
        } else if (!running) {
            setRunning(true)
        }
    }

    override fun onVoiceCommand(command: VoiceCommand): Boolean = when (command) {
        // On the Go search step anything that is not a command is the place: "Seoul Station".
        is VoiceCommand.Unknown -> {
            val q = command.text.trim().trimEnd('.', '!', '?')
            if (onGoSearch() && q.isNotEmpty()) {
                binding.walkingGoQuery.setText(q)
                searchPlaces(q, heard(q))
                true
            } else {
                false
            }
        }
        else -> false
    }

    private fun onGoSearch() = _binding != null && goMode && !navigating()

    /** "save this place as X" / "take me to X", from MainActivity. */
    fun onWalkCommand(command: WalkCommand) {
        if (_binding == null) return
        when (command) {
            WalkCommand.GoMode -> {
                // A plain "go" or "start" is a yes and arrives through takesWords. "Go to" and "navigation" start
                // over: taken as a yes, "Go to" set off for a place the user had not chosen (the logs).
                if (offer != null) {
                    offer = null
                    (activity as? MainActivity)?.dropWords()
                }
                enterGo(null)
            }
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
                val heard = heard(command.place)
                withLocation { here -> startNavigation(command.place, here, heard) }
            }
        }
    }

    private val goBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            services.speaker.stop()
            stopNavigation()
        }
    }

    private fun navigating() = navigator != null || target != null

    /** Go mode on: the search step first. "take me to X" arrives with [query]. */
    private fun enterGo(query: String?) {
        if (!goMode) {
            goMode = true
            main.removeCallbacks(goTicker)
            main.post(goTicker)
        }
        if (!navigating()) showGoSearch(query.orEmpty())
        if (query == null) {
            say(RoutePhrases.whereTo(services.lang))
            withLocation { }
        }
    }

    /** Typed or spoken search: saved places first, then nearby results, all with their distance. */
    private fun searchPlaces(query: String, heard: List<String> = listOf(query)) {
        goPanel.showSearching()
        withLocation { here ->
            val lang = services.lang
            val src = source
            if (src == null) {
                val saved = savedLike(heard)
                goPanel.showResults(saved.map { it to Beacon.distanceMetres(here, it.point) }, lang)
                if (saved.isEmpty()) say(RoutePhrases.notSetUp(lang))
                return@withLocation
            }
            findPlaces(src, heard, here)
        }
    }

    /** The recognizer's other guesses for what was just said, as places; [place] first. */
    private fun heard(place: String): List<String> = (activity as? MainActivity)?.heardPlaces(place) ?: listOf(place)

    private fun savedLike(heard: List<String>): List<Place> =
        places.all().filter { p -> heard.any { p.name.contains(it, ignoreCase = true) || it.contains(p.name, ignoreCase = true) } }

    /**
     * Finds what was heard and reads it out ([confirm]). Place names come out of the recognizer wrong
     * ("Go sale" for Seoul, "Turn on" for Cheonan in the logs) and the geocoder answers those words with
     * whatever is near ("Military Auto Sales", "Onyang-dong"). So in turn: the saved places, the known names
     * with the same sounds, the recognizer's guesses as they are (only results that have to do with the
     * words), then a known name one sound off. "home" without a saved home explains how to save one.
     */
    private fun findPlaces(src: RouteSource, heard: List<String>, here: LatLon) {
        val lang = services.lang
        val all = places.all()
        val saved = savedLike(heard)
        if (saved.isEmpty() && heard.any { PlaceNames.isHome(it) }) {
            offer = null
            if (goMode) goPanel.showResults(emptyList(), lang)
            say(RoutePhrases.noSavedHome(lang))
            return
        }
        // A newer search replaces this one: its answer must not be read out after the newer one's.
        val search = ++searches
        network.execute {
            fun metres(p: Place) = Beacon.distanceMetres(here, p.point)
            // A name that is known: the saved place, else the nearest one the geocoder has (its first answer
            // for "Asan" was on Guam). Far places are offered with their distance. One request per name: the
            // search service allows few a day, and a test with five to ten per phrase used them all up.
            fun known(name: String): Place? = all.firstOrNull { it.name == name }
                ?: src.geocodeFar(name, here).minByOrNull { metres(it) }?.takeIf { metres(it) <= FAR_KM * 1000 }
            val ranked = PlaceNames.ranked(heard, all.map { it.name } + PlaceNames.KNOWN)
            val same = ranked.filter { it.second == 0 }.take(1).mapNotNull { known(it.first) }
            var literal = emptyList<Place>()
            for (query in heard.take(if (same.isEmpty()) MAX_QUERIES else 1)) {
                literal = src.geocode(query, here).filter { PlaceNames.related(query, it.name) }.take(MAX_CANDIDATES)
                if (literal.isNotEmpty() || src.searchDown) break
            }
            var found = (saved + same + literal).distinctBy { it.name.lowercase() }.take(MAX_OFFERS)
            // A name one sound off is a weak guess: taken only when it is within walking reach ("Taskin" for
            // Tashkent matched Changwon, 250 km away).
            if (found.isEmpty() && !src.searchDown) {
                found = ranked.filter { it.second == 1 }.take(1).mapNotNull { (name, _) -> all.firstOrNull { it.name == name } ?: src.geocode(name, here).firstOrNull() }
            }
            // Nothing to offer: is it a real place that is too far? Exactly the name that was heard counts at
            // any distance (Tashkent); a looser match only nearby (a "Restaurant Krietsch" in Germany does not).
            val far = if (found.isEmpty() && !src.searchDown) {
                val anywhere = src.geocodeFar(heard.first(), here)
                anywhere.filter { p -> heard.any { it.equals(p.name, ignoreCase = true) } }.minByOrNull { metres(it) }
                    ?: anywhere.minByOrNull { metres(it) }?.takeIf { metres(it) <= FAR_KM * 1000 }
            } else {
                null
            }
            val down = found.isEmpty() && src.searchDown
            main.post {
                if (_binding == null || search != searches) return@post
                android.util.Log.i(
                    "Nungil",
                    "Go search $heard -> ${found.map { it.name }}" + (far?.let { ", too far: ${it.name}" } ?: "") + if (down) ", search down" else "",
                )
                if (down) {
                    offer = null
                    if (goMode) goPanel.showResults(emptyList(), lang)
                    say(RoutePhrases.searchUnavailable(lang))
                } else if (found.isEmpty() && far != null) {
                    offer = null
                    if (goMode) goPanel.showResults(emptyList(), lang)
                    say(RoutePhrases.tooFar(far.name, Beacon.distanceMetres(here, far.point), lang))
                } else {
                    // Read out, not only shown: "yes" goes there, "next" hears the following one.
                    confirm(found, 0, here)
                }
            }
        }
    }

    /**
     * Words Go mode takes before they are parsed as a command. While a found place waits for an answer:
     * "yes", "next", "the second one", "go", "the options". On "Where to?": a destination of the user's
     * own ("home", a saved name). True when [text] was taken.
     */
    fun takesWords(text: String): Boolean {
        if (_binding == null) return false
        val t = text.trim().trimEnd('.', '!', '?')
        val asked = offer
        if (asked != null) {
            val answer = GoAnswer.of(t)
            if (answer is GoAnswer.Other) return false
            onGoAnswer(asked, answer)
            return true
        }
        // "How far", "what's next", "where am I": before a place on "Where to?", and in walk mode too.
        GoQuestion.of(t)?.let { question ->
            // Nothing to repeat of a route that is not running: "repeat" keeps its meaning for the whole app.
            if (question == GoQuestion.REPEAT && !navigating()) return false
            answer(question)
            return true
        }
        if (!onGoSearch()) return false
        // "Go save" and "Save" were Seoul (the logs) and opened the Saved screen in the middle of Go.
        val place = t.replace(Regex("^(?:go|goto)\\s+(?:to\\s+)?", RegexOption.IGNORE_CASE), "")
        val taken = PlaceNames.isHome(t) || places.all().any { it.name.equals(t, ignoreCase = true) } || PlaceNames.alias(place) != null
        if (!taken) return false
        binding.walkingGoQuery.setText(PlaceNames.alias(place) ?: t)
        searchPlaces(place, heard(place))
        return true
    }

    /** "Where to?" is open and no found place waits for an answer: the next words are a place. */
    fun expectsPlace(): Boolean = onGoSearch() && offer == null

    /** While a place is expected: the names the recognizer should lean towards. */
    fun placeWords(): List<String> =
        if (expectsPlace()) (places.all().map { it.name } + PlaceNames.KNOWN).distinct() else emptyList()

    /** Step 1: a "Where to?" screen. Camera hidden, warnings quiet, a big Go button at the bottom. */
    private fun showGoSearch(query: String = "") {
        if (_binding == null) return
        goBack.isEnabled = false
        running = false
        services.beeper.stop()
        (activity as? MainActivity)?.setScreenTitle(getString(R.string.walking_go_title))
        binding.walkingTitle.setText(R.string.walking_go_where)
        binding.walkingStatus.visibility = View.GONE
        binding.walkingAnnouncement.visibility = View.GONE
        binding.walkingCameraCard.visibility = View.GONE
        binding.walkingMain.setText(R.string.walking_go_start)
        tintMain(R.attr.ngPrimary, R.attr.ngOnPrimary)
        goPanel.showSearch(query)
    }

    /** Step 2: like walk mode (camera, warnings) plus the arrow card; back or Stop returns to step 1. */
    private fun showGoRoute(place: Place) {
        if (_binding == null) return
        goBack.isEnabled = true
        announcer.reset()
        running = true
        (activity as? MainActivity)?.setScreenTitle(getString(R.string.walking_go_to, place.name))
        binding.walkingTitle.text = getString(R.string.walking_go_to, place.name)
        binding.walkingStatus.visibility = View.VISIBLE
        binding.walkingAnnouncement.visibility = View.VISIBLE
        binding.walkingCameraCard.visibility = View.VISIBLE
        binding.walkingMain.setText(R.string.walking_stop)
        tintMain(R.attr.ngDanger, R.attr.ngOnDanger)
        updateDirection()
    }

    private fun tintMain(bg: Int, fg: Int) {
        val c = requireContext()
        binding.walkingMain.backgroundTintList = android.content.res.ColorStateList.valueOf(c.resolveColorAttr(bg))
        binding.walkingMain.setTextColor(c.resolveColorAttr(fg))
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
        val now = SystemClock.elapsedRealtime()
        if (target != null && gps.update(now, location.accuracyM)) {
            Log.i(TAG, String.format(Locale.US, "Go GPS weak: %.0f m", location.accuracyM))
            queueNavigation(RoutePhrases.gpsWeak(services.lang))
        }
        val nav = navigator
        if (nav == null) {
            beaconFix(p, now)
            return
        }
        val announcement = nav.update(now, p, location.accuracyM)
        pace.add(now, nav.progressM, location.accuracyM)
        if (announcement == null) return
        when (announcement) {
            is Announcement.Arrived -> {
                target?.let { arrived(it) } ?: run { navigator = null }
                return
            }
            is Announcement.OffRoute -> {
                // Said only when a new route is really asked for: indoors the GPS wanders and "off the route"
                // came every five seconds.
                val place = target
                if (place == null || !gate.allow(now)) return
                requestRoute(p, place, reroute = true)
            }
            is Announcement.WrongWay -> Log.i(TAG, String.format(Locale.US, "Go wrong way at %.0f m along the route", nav.progressM))
            else -> Unit
        }
        queueNavigation(announcement.text)
    }

    /** By beacon (no route): the walker's speed from the shrinking distance, and the destination on the way in. */
    private fun beaconFix(p: LatLon, now: Long) {
        val place = target ?: return
        val metres = Beacon.distanceMetres(p, place.point).toFloat()
        val start = beaconStartM ?: metres.also { beaconStartM = it }
        pace.add(now, start - metres, location.accuracyM)
        if (beaconApproach.next(metres) != null) queueNavigation(RoutePhrases.approach(place.name, metres, null, services.lang))
    }

    /** The minute update: distance and time left, when GoProgress says it is time (spec §1). */
    private fun goUpdate() {
        val place = target ?: return
        val here = location.last ?: return
        val now = SystemClock.elapsedRealtime()
        val nav = navigator
        val state = nav?.peek(here)
        val remaining = state?.remainingM ?: Beacon.distanceMetres(here, place.point).toFloat()
        when (val verdict = progress.check(now, state?.takeIf { !it.final }?.toTargetM, lastNavAt, rerouting)) {
            GoProgress.Verdict.SAY -> {
                lastPutOff = null
                val seconds = pace.secondsFor(remaining)
                queueNavigation(RoutePhrases.progress(remaining, seconds, straight = nav == null, services.lang))
                progress.said(now)
                Log.i(TAG, String.format(Locale.US, "Go update: %.0f m left, %.2f m/s, %.1f min", remaining, pace.speedMps, seconds / 60))
            }
            GoProgress.Verdict.TURN_NEAR, GoProgress.Verdict.JUST_SPOKE, GoProgress.Verdict.REROUTING -> {
                if (verdict != lastPutOff) Log.i(TAG, "Go update put off: $verdict")
                lastPutOff = verdict
            }
            else -> Unit
        }
    }

    /**
     * Every 10 s, the way to go as a clock direction, so the walker does not stray from the route: towards the
     * point 20 m ahead on it (Navigator.aheadPoint), or the place itself without a route. Any other navigation
     * sentence or answer moves it 10 s on. Not while a new route is fetched, nor without a compass heading.
     */
    private fun goDirection() {
        val place = target ?: return
        val here = location.last ?: return
        val h = heading.headingDeg ?: return
        val now = SystemClock.elapsedRealtime()
        if (direction.check(now, null, Long.MIN_VALUE / 2, rerouting) != GoProgress.Verdict.SAY) return
        val towards = navigator?.aheadPoint(here) ?: place.point
        val clock = Beacon.clock(Beacon.bearingDeg(here, towards) - h)
        if (!queueNavigation(RoutePhrases.goClock(clock, services.lang), cue = true)) return
        direction.said(now)
        Log.i(TAG, "Go direction: $clock o'clock")
    }

    /** A walker's question (spec §3), answered at once. */
    private fun answer(question: GoQuestion) {
        val lang = services.lang
        fun reply(text: String) {
            Log.i(TAG, "Go asked: $question -> \"$text\"")
            // Asked just now: said at once, and the warnings that may wait (Announcer) wait for it.
            announcer.said(SystemClock.elapsedRealtime(), text)
            direction.said(SystemClock.elapsedRealtime())
            services.speaker.sayNow(text)
            _binding?.walkingAnnouncement?.text = text
        }
        when (question) {
            GoQuestion.WHERE_AM_I -> whereAmI()
            GoQuestion.QUIET, GoQuestion.UPDATES_ON -> {
                val quiet = question == GoQuestion.QUIET
                progress.setQuiet(quiet, SystemClock.elapsedRealtime())
                goSettings.quietUpdates = quiet
                reply(if (quiet) RoutePhrases.updatesOff(lang) else RoutePhrases.updatesOn(lang))
            }
            else -> {
                val place = target ?: return reply(RoutePhrases.noRouteRunning(lang))
                val here = location.last ?: return reply(WalkPhrases.waitingForLocation(lang))
                val state = navigator?.peek(here) ?: Beacon.distanceMetres(here, place.point).toFloat().let { d ->
                    GoState(place.point, RoutePhrases.headTo(place.name, lang), d, d, final = true)
                }
                when (question) {
                    GoQuestion.HOW_FAR -> {
                        reply(RoutePhrases.progress(state.remainingM, pace.secondsFor(state.remainingM), navigator == null, lang))
                        progress.said(SystemClock.elapsedRealtime())
                    }
                    GoQuestion.NEXT -> reply(RoutePhrases.next(state, place.name, lang))
                    GoQuestion.REPEAT -> reply(lastNav ?: RoutePhrases.next(state, place.name, lang))
                    GoQuestion.WHICH_WAY -> {
                        val h = heading.headingDeg ?: return reply(RoutePhrases.noHeading(lang))
                        val clock = Beacon.clock(Beacon.bearingDeg(here, state.target) - h)
                        reply(RoutePhrases.whichWay(clock, state.toTargetM, if (state.final) place.name else null, lang))
                    }
                    else -> Unit
                }
            }
        }
    }

    /**
     * "Where am I": the street from openrouteservice, near a saved place or the destination within
     * [NEAR_PLACE_M] (spec §6). Without it, the destination's distance and direction. Only from a fix of the
     * last 10 s (FixAge), and the answer, which comes after the network, waits behind the warnings like a turn.
     */
    private fun whereAmI() {
        withFreshLocation { here ->
            val lang = services.lang
            val near = (places.all() + listOfNotNull(target))
                .map { it to Beacon.distanceMetres(here, it.point) }
                .filter { it.second <= NEAR_PLACE_M }
                .minByOrNull { it.second }?.first?.name
            fun withoutStreet(): String {
                val place = target ?: return RoutePhrases.cannotLookUp(lang)
                val metres = Beacon.distanceMetres(here, place.point)
                val where = heading.headingDeg?.let { h -> WalkPhrases.beacon(place.name, metres, Beacon.clock(Beacon.bearingDeg(here, place.point) - h), lang) }
                    ?: "${place.name}, ${WalkPhrases.far(metres, lang)}."
                return RoutePhrases.cannotLookUp(lang) + " " + where
            }
            // Started for the question only: stopped again, after the answer, when no route needs it.
            fun done(text: String) {
                Log.i(TAG, "Go asked: ${GoQuestion.WHERE_AM_I} -> \"$text\"")
                queueNavigation(text)
                if (!navigating()) location.stop()
            }
            val src = source ?: return@withFreshLocation done(withoutStreet())
            network.execute {
                val street = src.reverse(here)
                main.post { if (_binding != null) done(street?.let { RoutePhrases.whereAmI(it, near, lang) } ?: withoutStreet()) }
            }
        }
    }

    /** [withLocation], but a last known place from before the updates started waits for the next fix (FixAge.usable). */
    private fun withFreshLocation(block: (LatLon) -> Unit) {
        withLocation { here ->
            if (FixAge.usable(location.fixAtMs, location.liveSinceMs, SystemClock.elapsedRealtime())) {
                block(here)
            } else {
                onFirstFix = block
                say(WalkPhrases.waitingForLocation(services.lang))
            }
        }
    }

    /**
     * Navigation waits for the next frame so obstacle warnings go first; without frames, or with the warnings
     * paused (no frame is ever spoken then), it speaks at once. A [cue] (the direction every 10 s) never takes
     * the place of a sentence still waiting, is not what "repeat" repeats, and does not hold the minute update
     * back; any other sentence moves the next cue 10 s on. False when the cue was not queued.
     */
    private fun queueNavigation(text: String, cue: Boolean = false): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (cue && pendingNav != null) return false
        if (!cue) {
            lastNav = text
            lastNavAt = now
            direction.said(now)
        }
        val alert = Alert(AlertKind.NAVIGATION, "nav:$text", text)
        if (!running || now - lastReportAt > REPORT_STALE_MS) say(text) else pendingNav = alert
        return true
    }

    private fun startNavigation(name: String, here: LatLon, heard: List<String> = listOf(name)) {
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
        findPlaces(src, heard, here)
    }

    /**
     * "Seoul Station, 400 metres away. Say yes to go, or next for another place." "Next" moves through the
     * candidates, "the second one" picks one, and anything that is not an answer is taken as another place.
     */
    private fun confirm(candidates: List<Place>, index: Int, here: LatLon) {
        val lang = services.lang
        if (goMode && index == 0) goPanel.showResults(candidates.map { it to Beacon.distanceMetres(here, it.point) }, lang)
        val place = candidates.getOrNull(index) ?: run {
            offer = null
            say(if (index == 0) WalkPhrases.unknownPlace(lang) else RoutePhrases.noMorePlaces(lang))
            return
        }
        val asked = Offer(candidates, index, here).also { offer = it }
        say(RoutePhrases.confirm(place.name, Beacon.distanceMetres(here, place.point), lang, more = index < candidates.lastIndex))
        listenForAnswer(asked)
    }

    private fun listenForAnswer(asked: Offer) {
        (activity as? MainActivity)?.dictationAccepts = { GoAnswer.of(it) !is GoAnswer.Other }
        services.askForWords(viewLifecycleOwner) { answer -> if (offer === asked) onGoAnswer(asked, GoAnswer.of(answer)) }
    }

    private fun onGoAnswer(asked: Offer, answer: GoAnswer) {
        // The question is answered: the words it was waiting for must not swallow the next phrase.
        (activity as? MainActivity)?.dropWords()
        when (answer) {
            GoAnswer.Yes -> go(asked, asked.index)
            GoAnswer.Next -> confirm(asked.candidates, asked.index + 1, asked.here)
            GoAnswer.Options -> {
                say(RoutePhrases.options(asked.candidates.map { it.name to Beacon.distanceMetres(asked.here, it.point) }, services.lang))
                listenForAnswer(asked)
            }
            is GoAnswer.Pick -> if (answer.index in asked.candidates.indices) go(asked, answer.index) else askAgain(asked)
            is GoAnswer.Other -> if (answer.text.length >= MIN_PLACE_CHARS) {
                offer = null
                if (goMode) binding.walkingGoQuery.setText(answer.text)
                searchPlaces(answer.text, heard(answer.text))
            } else {
                askAgain(asked)
            }
        }
    }

    private fun go(asked: Offer, index: Int) {
        offer = null
        requestRoute(asked.here, asked.candidates[index], reroute = false)
    }

    /** Once; a second unclear answer is most likely somebody else talking, and the question is dropped. */
    private fun askAgain(asked: Offer) {
        if (asked.askedAgain) {
            offer = null
            return
        }
        asked.askedAgain = true
        say(RoutePhrases.askAgain(services.lang))
        listenForAnswer(asked)
    }

    private fun requestRoute(from: LatLon, place: Place, reroute: Boolean) {
        val src = source ?: return
        target = place
        if (reroute) rerouting = true
        network.execute {
            val result = src.route(from, place)
            main.post { if (_binding != null) onRoute(result, place, reroute) }
        }
    }

    private fun onRoute(result: RouteResult, place: Place, reroute: Boolean) {
        val lang = services.lang
        rerouting = false
        when (result) {
            is RouteResult.Ok -> {
                navigator = Navigator(result.route, stepM, lang)
                // A new route measures progress from its own start; the speed found so far stays.
                pace.restart()
                if (!reroute) {
                    progress.start(SystemClock.elapsedRealtime())
                    direction.start(SystemClock.elapsedRealtime())
                }
                if (goMode) showGoRoute(place)
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
        beaconApproach = GoApproach()
        beaconStartM = null
        pace.restart()
        if (!progress.started) progress.start(SystemClock.elapsedRealtime())
        if (!direction.started) direction.start(SystemClock.elapsedRealtime())
        if (goMode) showGoRoute(place)
        status(getString(R.string.walking_status_beacon, place.name))
    }

    private fun stopNavigation() {
        navigator = null
        target = null
        lastNav = null
        rerouting = false
        progress.stop()
        direction.stop()
        if (offer != null) (activity as? MainActivity)?.dropWords()
        offer = null
        pendingNav = null
        onFirstFix = null
        location.stop()
        status(getString(R.string.walking_subtitle))
        if (goMode) showGoSearch()
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
        const val MIN_PLACE_CHARS = 3
        const val MAX_QUERIES = 2
        const val MAX_OFFERS = 4

        /** A known name farther than walking reach is still offered, with its distance, up to this. */
        const val FAR_KM = 300
        const val FALLBACK_MIN_SCORE = 0.5f
        const val WATCHDOG_EVERY_MS = 1_000L
        const val GO_TICK_MS = 200L
        const val DIRECTION_EVERY_MS = 10_000L
        const val TAG = "Nungil"

        /** "Where am I" names a saved place or the destination this near (spec §6). */
        const val NEAR_PLACE_M = 300.0
    }
}
