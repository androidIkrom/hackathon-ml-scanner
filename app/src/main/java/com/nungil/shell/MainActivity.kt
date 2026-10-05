package com.nungil.shell

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.snackbar.Snackbar
import com.nungil.R
import com.nungil.contract.Buzz
import com.nungil.contract.Dest
import com.nungil.contract.Lang
import com.nungil.contract.VoiceCommand
import com.nungil.contract.app.AppNavigator
import com.nungil.contract.app.AppServices
import com.nungil.contract.app.Beeper
import com.nungil.contract.app.CameraScreen
import com.nungil.contract.app.Haptics
import com.nungil.contract.app.Speaker
import com.nungil.contract.app.VoiceHandler
import com.nungil.core.scan.ScanPhrases
import com.nungil.core.ui.AppLanguage
import com.nungil.core.ui.CameraAction
import com.nungil.core.ui.CameraCommandPolicy
import com.nungil.core.ui.CommandRouter
import com.nungil.core.ui.HelpAnswer
import com.nungil.core.ui.LanguageChoice
import com.nungil.core.ui.PermissionOutcome
import com.nungil.core.ui.Phrase
import com.nungil.core.ui.RecognizerPolicy
import com.nungil.core.ui.Route
import com.nungil.core.ui.ScreenHelp
import com.nungil.core.ui.VoiceBargeIn
import com.nungil.core.ui.ShellPhrases
import com.nungil.core.ui.SpeechStop
import com.nungil.core.ui.TapHelpOffer
import com.nungil.core.ui.VoiceChoice
import com.nungil.core.voice.QuickAsk
import com.nungil.core.weather.Clock
import java.util.Calendar
import com.nungil.core.voice.VoiceCommandParser
import com.nungil.core.voice.WakeResult
import com.nungil.core.voice.WakeWord
import com.nungil.core.walk.GoQuestion
import com.nungil.core.walk.WalkCommand
import com.nungil.core.walk.WalkCommands
import com.nungil.walk.WalkFragment
import com.nungil.databinding.ActivityMainBinding
import com.nungil.design.isTalkBackOn
import com.nungil.design.openAppSettings
import com.nungil.items.AddItemFragmentArgs
import com.nungil.people.AddPersonFragmentArgs
import com.nungil.saved.SavedFragmentArgs
import com.nungil.scan.FrameAnswers
import com.nungil.scan.ScanFragmentArgs
import com.nungil.search.SearchFragmentArgs
import com.nungil.speech.ToneBeeper
import com.nungil.speech.TtsSpeaker
import com.nungil.speech.VibratorHaptics
import com.nungil.speech.VoiceGuide
import com.nungil.speech.VoiceInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owner I. The single activity: theme (light, dark or high contrast), toolbar, nav host, caption bar,
 * the AppServices every screen uses, the always-on microphone, voice-command routing, spoken help,
 * learner mode and the tap-to-hear voice guide.
 */
class MainActivity : AppCompatActivity(), AppServices, AppNavigator {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var prefs: AppPrefs
    private lateinit var tts: TtsSpeaker
    private lateinit var vibration: VibratorHaptics
    private val tones = ToneBeeper()
    private var voiceChoice: VoiceChoice? = null
    private lateinit var voice: VoiceInput
    private var askedMicThisRun = false
    private var lastNotUnderstoodAt = -RecognizerPolicy.NOT_UNDERSTOOD_GAP_MS
    private val voiceOnState = MutableStateFlow(false)

    /** "take me to X" said on another screen: delivered to walk mode once it opens. */
    private var pendingWalkCommand: WalkCommand? = null

    /** Taking commands: between the wake word ("Eye" / "눈길") and "Eye stop". Starts asleep. */
    private val awakeState = MutableStateFlow(false)

    /** A screen waiting for words (askForWords) while the always-on listener runs. */
    private var dictation: ((String) -> Unit)? = null
    private var dictationOwner: LifecycleOwner? = null

    /**
     * Set by a screen before [askForWords] when it waits for an answer, not a name ("yes", "next"): a
     * later recognizer guess that is such an answer then beats a misheard first one. Cleared with the claim.
     */
    var dictationAccepts: ((String) -> Boolean)? = null

    /**
     * Set with [dictationAccepts] by a screen whose answer may be taken from words still being said: "yes" to
     * "Is this it?" came 6 to 16 s late, or not at all, when the phrase had to end first (the logs).
     * Cleared with the claim.
     */
    var dictationEarly = false

    /**
     * A screen no longer waits for the words it asked for (it got its answer another way, or gave up).
     * Without this the next phrase went to the old question and was lost: "Turn on" had to be said twice.
     */
    fun dropWords() {
        dictation = null
        dictationOwner = null
        dictationAccepts = null
        dictationEarly = false
        tts.whenQuiet { }
    }

    /** Every recognizer guess for the phrase being handled. */
    private var lastGuesses: List<String> = emptyList()

    /** The recognizer's guesses for the last phrase, the one it trusts most first (Find: a misheard saved name). */
    fun heardGuesses(): List<String> = lastGuesses

    /**
     * What [place] may have been: the recognizer's other guesses for the same phrase, as places
     * ("Go so" came with "Go Seoul"; "Soul" with "Seoul"). [place] first, commands left out.
     */
    fun heardPlaces(place: String): List<String> =
        (listOf(place) + lastGuesses.mapNotNull { guess ->
            (walkCommand(guess) as? WalkCommand.GoTo)?.place
                ?: guess.takeIf { VoiceCommandParser.parse(it) is VoiceCommand.Unknown }
        }).map { it.trim().trimEnd('.', '!', '?') }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }

    private val guide = VoiceGuide()

    /** A tap on nothing offers the screen's instructions, once a screen visit (TapHelpOffer). */
    private val tapHelp = TapHelpOffer()

    /** The help offer waiting for its answer, if one is. */
    private var helpAsked: ((String) -> Unit)? = null

    /** When the user's voice last cut the app's own speech off (SpeechStop). */
    private var speechCutAt = Long.MIN_VALUE / 2

    /** When a bare "stop" last silenced the talking only (SpeechStop.talkingOnly). */
    private var talkStopAt = Long.MIN_VALUE / 2
    private var downX = 0f
    private var downY = 0f
    private var downAt = 0L

    private var afterMicGranted: (() -> Unit)? = null
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onMicPermission(granted)
    }

    /** The language the user chose for the screens (per-app locale, else the phone's language). */
    val uiLang: Lang
        get() {
            val appLocales = AppCompatDelegate.getApplicationLocales()
            val tag = if (appLocales.isEmpty) {
                ConfigurationCompat.getLocales(Resources.getSystem().configuration)[0]?.language
            } else {
                appLocales[0]?.language
            }
            return Lang.fromTag(tag)
        }

    /** The language of spoken sentences: the UI language, unless its voice is missing (then English). */
    override val lang: Lang
        get() = voiceChoice?.speak ?: uiLang
    override val speaker: Speaker get() = tts
    override val haptics: Haptics get() = vibration
    override val beeper: Beeper get() = tones
    override val navigator: AppNavigator get() = this

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = AppPrefs(this)
        val highContrast = prefs.highContrast
        if (highContrast) setTheme(R.style.Theme_Nungil_HighContrast)
        // High contrast is black in light and dark mode, so its bar icons are always light.
        val bars = if (highContrast) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(bars, bars)
        super.onCreate(savedInstanceState)
        // Changing the language recreates the activity: stay awake through it.
        if (savedInstanceState?.getBoolean(STATE_AWAKE) == true) awakeState.value = true
        // The wake word is the way in, so every opening of the app listens for it: the microphone turned off
        // stayed off, and "Eye start" went unheard with nothing to say why (the logs). Off lasts this visit only.
        if (savedInstanceState == null && !prefs.voiceOn) {
            Log.i(TAG, "Microphone on again: the app was opened")
            prefs.voiceOn = true
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // targetSdk 35 draws edge-to-edge on Android 15: keep content out from under the system bars.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        navController = (supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController
        binding.toolbar.setupWithNavController(navController, AppBarConfiguration(setOf(R.id.home)))
        // Home draws its own header (title, weather, clock) that scrolls with the cards.
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.toolbar.visibility = if (destination.id == R.id.home) View.GONE else View.VISIBLE
        }

        vibration = VibratorHaptics(this)
        tts = TtsSpeaker(
            context = this,
            wanted = { uiLang },
            onCaption = { text ->
                binding.caption.text = text
                binding.caption.visibility = if (text.isBlank()) View.GONE else View.VISIBLE
            },
            onVoiceChoice = ::onVoiceChoice,
        )
        voice = VoiceInput(
            context = this,
            language = { uiLang },
            appSaying = { tts.recentSpeech() },
            isAwake = { awakeState.value },
            holdSound = {
                if (tts.recentSpeech() != null) speechCutAt = SystemClock.elapsedRealtime()
                tts.holdForUser()
                tones.holdForUser()
            },
            releaseSound = {
                tts.resumeAfterUser()
                tones.resumeAfterUser()
            },
            understood = ::understood,
            answersNow = { dictationEarly && dictation != null && dictationAccepts?.invoke(it) == true },
            onGuesses = { guesses ->
                lastGuesses = guesses
                if (guesses.size > 1) Log.i(TAG, "Guesses: " + guesses.joinToString(" | "))
            },
            bias = { (currentScreen() as? WalkFragment)?.placeWords().orEmpty() },
            onHeard = ::onHeard,
            onProblem = ::onVoiceProblem,
        )
        tts.onQuiet = { voice.freshSession() }
        // Leaving a screen by any back (toolbar, gesture, button) silences what it was still saying.
        binding.toolbar.setNavigationOnClickListener {
            silenceAll()
            navController.navigateUp()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                silenceAll()
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        })
        prefs.takePendingAnnouncement()?.let { tts.say(it) }
        navController.addOnDestinationChangedListener { _, destination, _ ->
            tapHelp.onScreen(resources.getResourceEntryName(destination.id))
            if (prefs.learnerOn) tts.say(ScreenHelp.forScreen(resources.getResourceEntryName(destination.id), lang))
        }
        if (savedInstanceState == null && !prefs.onboarded) {
            prefs.onboarded = true // shown once, even if the user backs out of it
            open(Dest.Onboarding)
        }
    }

    override fun onResume() {
        super.onResume()
        // Listen the whole time the app is in front (never in the background); on by default.
        if (prefs.voiceOn) {
            if (hasMic()) {
                voice.start()
            } else if (!askedMicThisRun) {
                askedMicThisRun = true
                ensureMic {
                    voice.start()
                    voiceOnState.value = true
                }
            }
        }
        voiceOnState.value = voice.isOn
    }

    override fun onPause() {
        voice.stop()
        super.onPause()
    }

    override fun onDestroy() {
        voice.destroy()
        tts.shutdown()
        tones.release()
        super.onDestroy()
    }

    override fun askForWords(owner: LifecycleOwner, onText: (String) -> Unit) {
        if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) return
        tones.stop()
        if (voice.isOn) {
            // Screens say their question and then ask for the words. Opening the microphone at once cut that
            // question off before it was heard, so the claim, the chime and the quiet wait until it has been
            // said (claiming earlier would hand the app's own question over as the answer).
            tts.whenQuiet {
                if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) return@whenQuiet
                // One microphone, one owner: the always-on listener hands the next non-command words over.
                dictation = onText
                dictationOwner = owner
                voice.talkNow()
            }
            owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    if (dictationOwner === owner) {
                        dictation = null
                        dictationOwner = null
                    }
                }
            })
            return
        }
        ensureMic {
            haptics.buzz(Buzz.TAP)
            tts.whenQuiet {
                voice.listenOnce { text ->
                    if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) onText(text)
                }
            }
        }
    }

    // ---- Navigation -------------------------------------------------------------------------------

    override fun open(dest: Dest) {
        when (dest) {
            Dest.Home -> navController.popBackStack(R.id.home, false)
            Dest.ScanHub -> go(R.id.scan_hub)
            is Dest.Scan -> go(R.id.scan, ScanFragmentArgs(dest.mode).toBundle())
            Dest.Walk -> go(R.id.walk)
            is Dest.Search -> go(R.id.search, SearchFragmentArgs(dest.query).toBundle())
            is Dest.Saved -> go(R.id.saved, SavedFragmentArgs(dest.tab?.ordinal ?: -1).toBundle())
            Dest.Settings -> go(R.id.settings)
            Dest.History -> go(R.id.history)
            is Dest.AddPerson -> go(R.id.add_person, AddPersonFragmentArgs(dest.name).toBundle())
            is Dest.AddItem -> go(R.id.add_item, AddItemFragmentArgs(dest.kind, dest.name).toBundle())
            Dest.Reader -> go(R.id.reader)
            Dest.Onboarding -> go(R.id.onboarding)
        }
    }

    /** Through the dispatcher, so a screen's own back step (Go mode: route back to search) comes first. */
    override fun back() {
        onBackPressedDispatcher.onBackPressed()
    }

    /** popUpTo the same destination, so "full scan" said twice never stacks two scan screens. */
    private fun go(id: Int, args: Bundle? = null) {
        val options = NavOptions.Builder().setLaunchSingleTop(true).setPopUpTo(id, true).build()
        navController.navigate(id, args, options)
    }

    /** The screen on top, which gets voice commands first. */
    private fun currentScreen(): Fragment? =
        supportFragmentManager.findFragmentById(R.id.nav_host)?.childFragmentManager?.primaryNavigationFragment

    // ---- Voice commands ---------------------------------------------------------------------------

    /** A recognizer guess that does something. While a screen waits for a name, only the first guess counts. */
    private fun understood(guess: String): Boolean {
        if (dictation != null) return dictationAccepts?.invoke(guess) == true
        // While a place is expected the first guess is the place: a later guess that happens to be a command
        // ("Save" among the guesses for "Seoul") opened the Saved screen in the middle of Go.
        if ((currentScreen() as? WalkFragment)?.expectsPlace() == true) return false
        return when (val wake = WakeWord.decide(guess, awakeState.value) { isCommand(it) }) {
            WakeResult.Ignore -> false
            WakeResult.Wake, WakeResult.Sleep -> true
            is WakeResult.Command -> isCommand(wake.text)
        }
    }

    private fun onHeard(heard: String) {
        // The answer to the help offer, though "ok", "go ahead" or "help" are commands too, and with no wake
        // word: the tap that asked was the user's, and the app is mostly asleep on Home (the phone).
        helpAsked?.let { asked ->
            if (HelpAnswer.of(heard) != null) {
                dropWords()
                asked(heard)
                return
            }
        }
        val wake = WakeWord.decide(heard, awakeState.value) { isCommand(it) }
        val text = when (wake) {
            WakeResult.Ignore -> {
                Log.i(TAG, "Asleep, ignored \"$heard\"")
                return
            }
            WakeResult.Wake -> {
                wakeUp()
                return
            }
            WakeResult.Sleep -> {
                goToSleep()
                return
            }
            is WakeResult.Command -> {
                if (wake.wake) wakeUp()
                wake.text
            }
        }
        // The time, the date and the weather are answered on every screen (on "Where to?" they were searched
        // as places). Not while a screen waits for a name; a screen that waits for "yes" or "next" keeps waiting.
        if (dictation == null || dictationAccepts != null) QuickAsk.of(text)?.let { ask ->
            Log.i(TAG, "Heard \"$text\" -> $ask")
            answer(ask)
            return
        }
        // Go mode first: on "Where to?" a saved place or "home" is the destination, not the Home screen, and an
        // answer to a place that was read out ("go", "the options") is not a command.
        val asking = currentScreen()
        if (asking is WalkFragment && asking.takesWords(text)) {
            Log.i(TAG, "Heard \"$text\" -> Go mode")
            return
        }
        // "Go to" alone, with "Go to InTown" among the guesses (the logs): the guess with the place is the one.
        val heardWalk = walkCommand(text)
        val better = if (heardWalk == WalkCommand.GoMode) lastGuesses.firstNotNullOfOrNull { walkCommand(it) as? WalkCommand.GoTo } else null
        (better ?: heardWalk)?.let { walk ->
            Log.i(TAG, "Heard \"$text\" -> $walk")
            silenceAll()
            val screen = currentScreen()
            if (screen is WalkFragment) {
                screen.onWalkCommand(walk)
            } else {
                pendingWalkCommand = walk
                open(Dest.Walk)
            }
            return
        }
        val command = VoiceCommandParser.parse(text)
        Log.i(TAG, "Heard \"$text\" -> $command")
        // The app's own sentence, one word misheard, is not a command: "Hold the phone still." came back as
        // "Close the phone" and went Back in the middle of learning an item (the logs).
        if (command !is VoiceCommand.Unknown && VoiceBargeIn.mostlyEcho(text, tts.recentSpeech())) {
            Log.i(TAG, "Ignored \"$text\": the app's own words")
            return
        }
        // "Stop" alone while the app talks stops the talking only: Go mode keeps guiding, a scan keeps scanning.
        // Said while the app is quiet, or with more to it ("stop navigation"), it stops the screen as before.
        val talking = tts.recentSpeech() != null || SystemClock.elapsedRealtime() - speechCutAt < SPEECH_CUT_MS
        if (SpeechStop.isBare(text) && talking) {
            val now = SystemClock.elapsedRealtime()
            if (SpeechStop.talkingOnly(now - talkStopAt)) {
                talkStopAt = now
                Log.i(TAG, "Stop: the speech only")
                silenceAll()
                return
            }
            // Said again: the screen stops too.
            talkStopAt = Long.MIN_VALUE / 2
            Log.i(TAG, "Stop again: the screen too")
        }
        val claim = dictation
        if (claim != null && command is VoiceCommand.Unknown) {
            dictation = null
            dictationOwner = null
            dictationAccepts = null
            dictationEarly = false
            claim(command.text)
            return
        }
        // A screen that takes free words (Go: the place to search) gets them before "I did not understand".
        if (command is VoiceCommand.Unknown && (currentScreen() as? VoiceHandler)?.onVoiceCommand(command) == true) return
        // An always-on microphone also hears people nearby: say "I did not understand" only now and then.
        if (command is VoiceCommand.Unknown) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastNotUnderstoodAt < RecognizerPolicy.NOT_UNDERSTOOD_GAP_MS) return
            lastNotUnderstoodAt = now
        }
        handleCommand(command)
    }

    private fun onVoiceProblem(phrase: Phrase) {
        if (phrase == Phrase.MIC_NEEDED || phrase == Phrase.VOICE_UNAVAILABLE) {
            Log.i(TAG, "Microphone off: $phrase")
            prefs.voiceOn = false
            voiceOnState.value = false
        }
        say(phrase)
    }

    /** Runs one parsed command: global ones here, the rest on the current screen first. Main thread. */
    fun handleCommand(command: VoiceCommand) {
        if (!CommandRouter.isGlobal(command)) {
            // Camera commands are taken the same way on every camera screen, before the screen's own handler.
            (currentScreen() as? CameraScreen)?.let { cam ->
                val action = CameraCommandPolicy.decide(command, cam.switchable != null, cam.isWorking)
                if (action != null) {
                    Log.i(TAG, "Camera command $command -> $action")
                    onCameraCommand(cam, action)
                    return
                }
            }
            val screen = currentScreen() as? VoiceHandler
            if (screen?.onVoiceCommand(command) == true) return
        }
        perform(CommandRouter.route(command))
    }

    private fun onCameraCommand(screen: CameraScreen, action: CameraAction) {
        when (action) {
            is CameraAction.Switch -> screen.switchable?.let {
                it.useCamera(action.to)
                tts.sayNow(ScanPhrases.cameraSwitched(it.facing, lang))
            }
            CameraAction.BackCameraOnly -> tts.sayNow(ScanPhrases.backCameraOnly(lang))
            CameraAction.DescribeCentre -> frameAnswers(screen)?.what(screen.lastFrame(), lang)
            CameraAction.NameFace -> frameAnswers(screen)?.who(screen.lastFrame(), lang)
            CameraAction.Pause -> screen.pause()
            CameraAction.Leave -> {
                silenceAll()
                back()
            }
            CameraAction.Resume -> screen.resume()
        }
    }

    /** What / who is this for the camera screen on top; one at a time, closed with that screen's view. */
    private var answers: FrameAnswers? = null
    private var answersOwner: LifecycleOwner? = null

    private fun frameAnswers(screen: CameraScreen): FrameAnswers? {
        val owner = (screen as? Fragment)?.takeIf { it.view != null }?.viewLifecycleOwner ?: return null
        if (answersOwner !== owner) {
            answers?.close()
            answers = FrameAnswers(this, owner) { tts.sayNow(it) }
            answersOwner = owner
            owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    if (answersOwner === owner) {
                        answers?.close()
                        answers = null
                        answersOwner = null
                    }
                }
            })
        }
        return answers
    }

    private fun perform(route: Route) {
        when (route) {
            is Route.Open -> {
                silenceAll()
                open(route.dest)
            }
            Route.GoBack -> {
                silenceAll()
                back()
            }
            Route.RepeatLast -> if (!tts.repeatLast()) say(Phrase.NOTHING_TO_REPEAT)
            is Route.SpeakHelp -> tts.sayNow(helpText(route.topic))
            is Route.SetLearner -> setLearner(route.on)
            Route.StopListening -> setVoiceOn(false)
            is Route.SwitchLanguage -> setLanguage(AppLanguage.forLang(route.lang))
            is Route.OpenAndSay -> {
                silenceAll()
                open(route.dest)
                say(route.phrase)
            }
            is Route.Say -> say(route.phrase)
            Route.StopSpeaking -> silenceAll()
        }
    }

    private fun say(phrase: Phrase) = tts.say(ShellPhrases.text(phrase, lang))

    /** Help for a spoken topic, else for the screen on top. */
    private fun helpText(topic: String?): String {
        topic?.let { t -> ScreenHelp.forTopic(t, lang)?.let { return it } }
        val screen = navController.currentDestination?.id?.let { resources.getResourceEntryName(it) } ?: "home"
        return ScreenHelp.forScreen(screen, lang)
    }

    // ---- Voice guide ------------------------------------------------------------------------------

    /**
     * A tap (short, without moving) speaks what is under the finger while TalkBack is off. The event is
     * always passed on, so the tapped control works as usual.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // TalkBack handles touches its own way.
        if (::prefs.isInitialized && !isTalkBackOn()) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX
                    downY = ev.rawY
                    downAt = ev.eventTime
                }
                MotionEvent.ACTION_UP -> {
                    val slop = ViewConfiguration.get(this).scaledTouchSlop
                    val still = kotlin.math.abs(ev.rawX - downX) < slop && kotlin.math.abs(ev.rawY - downY) < slop
                    if (still && ev.eventTime - downAt < ViewConfiguration.getLongPressTimeout()) {
                        val x = ev.rawX.toInt()
                        val y = ev.rawY.toInt()
                        // A tap on nothing asks first (TapHelpOffer); the voice guide names what is under the finger.
                        val owner = currentScreen()?.takeIf { it.view != null }?.viewLifecycleOwner
                        val canHear = owner != null && dictation == null
                        when (tapHelp.onTap(guide.isEmptyAt(binding.navHost, x, y), prefs.voiceGuideOn, canHear)) {
                            TapHelpOffer.Tap.ASK -> owner?.let { offerHelp(it) }
                            TapHelpOffer.Tap.DESCRIBE -> guide.describeAt(binding.navHost, x, y, lang)?.let { tts.say(it) }
                            TapHelpOffer.Tap.NOTHING -> Unit
                        }
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    /**
     * A tap on nothing (TapHelpOffer): "Do you want instructions for this screen?", and the screen's help on
     * "yes", asleep or awake (its answer needs no wake word). Not while another question waits. An answer that
     * does not come in [HELP_ANSWER_MS] lets the microphone go, so the next words are not taken for it.
     */
    private fun offerHelp(owner: LifecycleOwner) {
        Log.i(TAG, "Tap on nothing: the screen's instructions offered")
        tts.say(TapHelpOffer.question(lang))
        dictationAccepts = { HelpAnswer.of(it) != null }
        dictationEarly = true
        val onAnswer: (String) -> Unit = { answer ->
            helpAsked = null
            Log.i(TAG, "Instructions offered, answer \"$answer\"")
            when (HelpAnswer.of(answer)) {
                true -> tts.say(helpText(null))
                false -> tts.say(TapHelpOffer.declined(lang))
                null -> Unit
            }
        }
        helpAsked = onAnswer
        askForWords(owner, onAnswer)
        binding.root.postDelayed({
            if (helpAsked === onAnswer) helpAsked = null
            if (dictation === onAnswer) dropWords()
        }, HELP_ANSWER_MS)
    }

    // ---- App settings -----------------------------------------------------------------------------

    val languageChoice: LanguageChoice
        get() = AppLanguage.choiceOf(AppCompatDelegate.getApplicationLocales().toLanguageTags())

    /** Switches the UI, speech and recognition language; the activity is recreated by AppCompat. */
    fun setLanguage(choice: LanguageChoice) {
        val tag = AppLanguage.tag(choice)
        val target = if (choice == LanguageChoice.SYSTEM) {
            Lang.fromTag(ConfigurationCompat.getLocales(Resources.getSystem().configuration)[0]?.language)
        } else {
            Lang.fromTag(tag)
        }
        val sentence = ShellPhrases.text(Phrase.LANGUAGE_SET, target)
        if (choice == languageChoice) {
            tts.say(sentence)
            return
        }
        prefs.setPendingAnnouncement(sentence)
        val locales = if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
        AppCompatDelegate.setApplicationLocales(locales)
    }

    val highContrast: Boolean get() = prefs.highContrast

    fun setHighContrast(on: Boolean) {
        if (prefs.highContrast == on) return
        prefs.highContrast = on
        recreate()
    }

    val learnerOn: Boolean get() = prefs.learnerOn

    fun setLearner(on: Boolean) {
        prefs.learnerOn = on
        say(if (on) Phrase.LEARNER_ON else Phrase.LEARNER_OFF)
    }

    /** Always-on voice commands, for the Home microphone button. */
    val voiceOn: StateFlow<Boolean> get() = voiceOnState.asStateFlow()

    /** Turns always-on voice on (the same as pressing the microphone) or off. The chime confirms it. */
    fun setVoiceOn(on: Boolean) {
        if (on) {
            talkNow()
            return
        }
        Log.i(TAG, "Microphone off by the user")
        silenceAll()
        awakeState.value = false
        prefs.voiceOn = false
        voice.stop(chime = true)
        voiceOnState.value = false
    }

    /**
     * A microphone button: every app sound (instructions, announcements, beeps) stops at once and
     * stays off while the user talks; the microphone is turned on if it was off.
     */
    fun talkNow() {
        silenceAll()
        ensureMic {
            prefs.voiceOn = true
            awakeState.value = true
            voice.talkNow()
            voiceOnState.value = true
        }
    }

    private fun walkCommand(text: String): WalkCommand? =
        WalkCommands.parse(text) { VoiceCommandParser.parse(it) !is VoiceCommand.Unknown }

    private fun isCommand(text: String): Boolean =
        VoiceCommandParser.parse(text) !is VoiceCommand.Unknown || walkCommand(text) != null || QuickAsk.of(text) != null ||
            GoQuestion.of(text) != null || SpeechStop.isBare(text)

    private fun answer(ask: QuickAsk) {
        val now = Calendar.getInstance()
        when (ask) {
            QuickAsk.TIME -> tts.say(Clock.timeSpoken(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), lang))
            QuickAsk.DATE -> tts.say(
                Clock.dateSpoken(
                    now.get(Calendar.MONTH) + 1,
                    now.get(Calendar.DAY_OF_MONTH),
                    // Calendar: 1 = Sunday; Clock: 1 = Monday .. 7 = Sunday.
                    (now.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1,
                    lang,
                ),
            )
            // Y's weather label in the app bar says the date and the full report when tapped.
            QuickAsk.WEATHER -> findViewById<View>(R.id.app_weather)?.performClick()
        }
    }

    /** Go mode: walk mode with the "Where to?" search and the direction card. */
    fun openGoMode() {
        silenceAll()
        pendingWalkCommand = WalkCommand.GoMode
        open(Dest.Walk)
    }

    /** A screen may rename itself (walk mode shows "Go somewhere" in Go mode). */
    fun setScreenTitle(title: CharSequence) {
        binding.toolbar.title = title
    }

    /** Walk mode takes the command it was opened for. */
    fun takePendingWalkCommand(): WalkCommand? = pendingWalkCommand.also { pendingWalkCommand = null }

    /** Taking commands (true) or waiting for the wake word (false), for the Home microphone button. */
    val awake: StateFlow<Boolean> get() = awakeState.asStateFlow()

    private fun wakeUp() {
        Log.i(TAG, "Awake")
        awakeState.value = true
        voice.chimeOn()
        haptics.buzz(Buzz.TAP)
    }

    /** "Eye stop": every sound stops and only the wake word is answered until it is said again. */
    private fun goToSleep() {
        Log.i(TAG, "Asleep")
        // Whatever the screen is doing (a scan, a search) stops too, then every sound.
        (currentScreen() as? VoiceHandler)?.onVoiceCommand(VoiceCommand.Stop)
        awakeState.value = false
        dictation = null
        dictationOwner = null
        dictationAccepts = null
        dictationEarly = false
        silenceAll()
        voice.chimeOff()
    }

    private fun silenceAll() {
        tts.stop()
        tones.stop()
    }

    private fun hasMic(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Runs [then] once the microphone permission is granted, asking for it first if needed. */
    private fun ensureMic(then: () -> Unit) {
        if (hasMic()) {
            then()
            return
        }
        afterMicGranted = then
        if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) say(Phrase.MIC_NEEDED)
        micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun onMicPermission(granted: Boolean) {
        val then = afterMicGranted
        afterMicGranted = null
        when (PermissionOutcome.of(granted, shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO))) {
            PermissionOutcome.GRANTED -> then?.invoke()
            PermissionOutcome.DENIED -> say(Phrase.MIC_NEEDED)
            PermissionOutcome.BLOCKED -> {
                say(Phrase.MIC_BLOCKED)
                Snackbar.make(binding.root, R.string.voice_mic_blocked, Snackbar.LENGTH_LONG)
                    .setAction(R.string.voice_open_settings) { openAppSettings() }
                    .show()
            }
        }
    }

    private fun onVoiceChoice(choice: VoiceChoice) {
        voiceChoice = choice
        if (choice.notice == null) return
        Snackbar.make(binding.root, R.string.voice_korean_voice_missing, Snackbar.LENGTH_LONG)
            .setAction(R.string.voice_install) {
                runCatching { startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) }
            }
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_AWAKE, awakeState.value)
    }

    private companion object {
        const val TAG = "Nungil"
        const val STATE_AWAKE = "awake"

        /** How long after the user's voice cut the app off a "stop" still means the talking (SpeechStop). */
        const val SPEECH_CUT_MS = 10_000L

        /** How long the help offer waits for "yes" once it has been asked. */
        const val HELP_ANSWER_MS = 15_000L
    }
}
