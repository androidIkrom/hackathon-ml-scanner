package com.nungil.core.ui

import com.nungil.contract.Dest
import com.nungil.contract.Facing
import com.nungil.contract.VoiceCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraCommandPolicyTest {
    private fun decide(c: VoiceCommand, canSwitch: Boolean = true, working: Boolean = true) =
        CameraCommandPolicy.decide(c, canSwitch, working)

    @Test fun switchCameraSwitchesWhenItCan() {
        assertEquals(CameraAction.Switch(Facing.FRONT), decide(VoiceCommand.SwitchCamera(Facing.FRONT)))
        assertEquals(CameraAction.Switch(null), decide(VoiceCommand.SwitchCamera()))
    }

    @Test fun backCameraOnlyWhenItCannot() =
        assertEquals(CameraAction.BackCameraOnly, decide(VoiceCommand.SwitchCamera(Facing.FRONT), canSwitch = false))

    @Test fun whatAndWhoIsThis() {
        for (canSwitch in listOf(true, false)) for (working in listOf(true, false)) {
            assertEquals(CameraAction.DescribeCentre, decide(VoiceCommand.WhatIsThis, canSwitch, working))
            assertEquals(CameraAction.NameFace, decide(VoiceCommand.WhoIsThis, canSwitch, working))
        }
    }

    @Test fun stopPausesWorkGoingOn() = assertEquals(CameraAction.Pause, decide(VoiceCommand.Stop, working = true))

    @Test fun stopLeavesWhenAlreadyPaused() = assertEquals(CameraAction.Leave, decide(VoiceCommand.Stop, working = false))

    @Test fun startResumes() {
        assertEquals(CameraAction.Resume, decide(VoiceCommand.Start, working = false))
        assertEquals(CameraAction.Resume, decide(VoiceCommand.Start, working = true))
    }

    @Test fun otherCommandsAreTheScreens() {
        for (c in listOf(VoiceCommand.Back, VoiceCommand.Delete, VoiceCommand.ReadText, VoiceCommand.Unknown("x"), VoiceCommand.Go(Dest.Home))) {
            assertNull(c.toString(), decide(c))
        }
    }
}
