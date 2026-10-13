package com.nungil.core.ui

import com.nungil.contract.ModelChoice
import com.nungil.contract.ScanSettings
import com.nungil.data.SettingsDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsDefaultsTest {
    @Test fun yoloIsTheDefault() = assertEquals(ModelChoice.YOLO, ScanSettings().model)

    @Test fun theOldDefaultMovesToYoloOnce() {
        // The user, 2026-10-13: "make YOLO26 the default"; the phone had Accurate saved.
        assertTrue(SettingsDefaults.movesToYolo("ACCURATE"))
        assertTrue(SettingsDefaults.movesToYolo(null))
        assertFalse(SettingsDefaults.movesToYolo("FAST"))
        assertFalse(SettingsDefaults.movesToYolo("LIGHT"))
    }
}
