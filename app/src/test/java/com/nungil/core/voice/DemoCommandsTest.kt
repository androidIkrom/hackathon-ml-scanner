package com.nungil.core.voice

import com.nungil.contract.Dest
import com.nungil.contract.Lang
import com.nungil.contract.SavedTab
import com.nungil.contract.ScanMode
import com.nungil.contract.VoiceCommand
import org.junit.Assert.assertEquals
import org.junit.Test

/** Every phrase in docs/design/demo-script.md does what the script says. Keep the two in step. */
class DemoCommandsTest {
    private fun heard(text: String, awake: Boolean): Any =
        when (val r = WakeWord.decide(text, awake) { VoiceCommandParser.parse(it) !is VoiceCommand.Unknown }) {
            is WakeResult.Command -> VoiceCommandParser.parse(r.text)
            else -> r
        }

    @Test fun switchTheLanguage() {
        assertEquals(WakeResult.Wake, heard("Eye", awake = false))
        assertEquals(VoiceCommand.SetLanguage(Lang.KO), heard("Korean", awake = true))
        assertEquals(VoiceCommand.SetLanguage(Lang.KO), heard("한국어", awake = true))
        assertEquals(VoiceCommand.Go(Dest.Scan(ScanMode.FULL)), heard("눈길아, 주변 둘러보기", awake = false))
    }

    @Test fun handsFreeInEnglish() {
        assertEquals(WakeResult.Sleep, heard("Eye stop", awake = true))
        assertEquals(WakeResult.Ignore, heard("saved", awake = false))
        assertEquals(VoiceCommand.Go(Dest.Saved(null)), heard("Eye, saved", awake = false))
        assertEquals(VoiceCommand.Go(Dest.Scan(ScanMode.FULL)), heard("full scan", awake = true))
        assertEquals(VoiceCommand.Stop, heard("stop", awake = true))
        assertEquals(VoiceCommand.Back, heard("back", awake = true))
    }

    @Test fun handsFreeInKorean() {
        assertEquals(WakeResult.Sleep, heard("눈길아 그만", awake = true))
        assertEquals(WakeResult.Ignore, heard("저장한 것", awake = false))
        assertEquals(VoiceCommand.Go(Dest.Saved(null)), heard("눈길아, 저장한 것", awake = false))
        assertEquals(VoiceCommand.Go(Dest.Scan(ScanMode.FULL)), heard("주변 둘러보기", awake = true))
        assertEquals(VoiceCommand.Stop, heard("멈춰", awake = true))
        assertEquals(VoiceCommand.Back, heard("뒤로 가", awake = true))
    }

    @Test fun savedTabIsUnset() = assertEquals(null, (VoiceCommandParser.parse("saved") as VoiceCommand.Go).dest.let { (it as Dest.Saved).tab as SavedTab? })
}
