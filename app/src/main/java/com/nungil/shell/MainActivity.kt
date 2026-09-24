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
import com.nungil.contract.app.Haptics
import com.nungil.contract.app.Speaker
import com.nungil.contract.app.VoiceHandler
import com.nungil.core.ui.AppLanguage
import com.nungil.core.ui.CommandRouter
import com.nungil.core.ui.LanguageChoice
import com.nungil.core.ui.PermissionOutcome
import com.nungil.core.ui.Phrase
import com.nungil.core.ui.RecognizerPolicy
import com.nungil.core.ui.Route
import com.nungil.core.ui.ScreenHelp
import com.nungil.core.ui.ShellPhrases
import com.nungil.core.ui.VoiceChoice
import com.nungil.core.voice.VoiceCommandParser
import com.nungil.core.voice.WakeResult
import com.nungil.core.voice.WakeWord
import com.nungil.core.walk.WalkCommand
import com.nungil.core.walk.WalkCommands
import com.nungil.walk.WalkFragment
import com.nungil.databinding.ActivityMainBinding
import com.nungil.design.isTalkBackOn
import com.nungil.design.openAppSettings
import com.nungil.items.AddItemFragmentArgs
import com.nungil.people.AddPersonFragmentArgs
import com.nungil.saved.SavedFragmentArgs
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

    private val guide = VoiceGuide()
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
                tts.holdForUser()
                tones.holdForUser()
            },
            releaseSound = {
                tts.resumeAfterUser()
                tones.resumeAfterUser()
            },
            onHeard = ::onHeard,
            onProblem = ::onVoiceProblem,
        )
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
        silenceAll()
        if (voice.isOn) {
            voice.talkNow()
            // One microphone, one owner: the always-on listener hands the next non-command words over.
            dictation = onText
            dictationOwner = owner
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
            voice.listenOnce { text ->
                if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) onText(text)
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

    override fun back() {
        if (!navController.popBackStack()) finish()
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

    private fun onHeard(heard: String) {
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
        walkCommand(text)?.let { walk ->
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
        val claim = dictation
        if (claim != null && command is VoiceCommand.Unknown) {
            dictation = null
            dictationOwner = null
            claim(command.text)
            return
        }
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
            prefs.voiceOn = false
            voiceOnState.value = false
        }
        say(phrase)
    }

    /** Runs one parsed command: global ones here, the rest on the current screen first. Main thread. */
    fun handleCommand(command: VoiceCommand) {
        if (!CommandRouter.isGlobal(command)) {
            val screen = currentScreen() as? VoiceHandler
            if (screen?.onVoiceCommand(command) == true) return
        }
        perform(CommandRouter.route(command))
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
        if (::prefs.isInitialized && prefs.voiceGuideOn && !isTalkBackOn()) {
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
                        guide.describeAt(binding.navHost, ev.rawX.toInt(), ev.rawY.toInt(), lang)?.let { tts.say(it) }
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
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
        VoiceCommandParser.parse(text) !is VoiceCommand.Unknown || walkCommand(text) != null

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

    private companion object {
        const val TAG = "Nungil"
    }
}
