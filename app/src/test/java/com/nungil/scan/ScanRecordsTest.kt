package com.nungil.scan

import com.nungil.contract.Lang
import com.nungil.contract.ScanMode
import com.nungil.core.scan.ColorName
import com.nungil.core.scan.ObjectSummary
import com.nungil.core.scan.ScanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanRecordsTest {
    private val result = ScanResult(
        mode = ScanMode.FULL,
        startedAtMs = 1_000L,
        coveragePercent = 80,
        summary = "I scanned 80 percent of the room. Around you: 3 blue chairs on your right.",
        objects = listOf(ObjectSummary("chair", 3, ColorName.BLUE, 90f), ObjectSummary("person", 1, null, 350f)),
    )

    @Test fun scanRow() {
        val row = ScanRecords.scanEntity(result, Lang.KO)
        assertEquals(1_000L, row.startedAt)
        assertEquals("FULL", row.mode)
        assertEquals(80, row.coveragePercent)
        assertEquals("ko", row.lang)
    }

    @Test fun objectRows() {
        val rows = ScanRecords.objectEntities(result)
        assertEquals("BLUE", rows[0].colorName)
        assertEquals(2, rows[0].sector8)
        assertNull(rows[1].colorName)
        assertEquals(0, rows[1].sector8)
        assertEquals(0L, rows[0].scanId)
    }
}
