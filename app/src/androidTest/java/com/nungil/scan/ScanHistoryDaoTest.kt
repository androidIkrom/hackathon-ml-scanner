package com.nungil.scan

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nungil.contract.Lang
import com.nungil.contract.ScanMode
import com.nungil.core.scan.ColorName
import com.nungil.core.scan.ObjectSummary
import com.nungil.core.scan.ScanResult
import com.nungil.data.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Proves the history write path on a real device: parent and children in one transaction, cascade delete. */
@RunWith(AndroidJUnit4::class)
class ScanHistoryDaoTest {
    private lateinit var db: AppDatabase

    private val result = ScanResult(
        mode = ScanMode.FULL,
        startedAtMs = 42L,
        coveragePercent = 100,
        summary = "Around you: 3 blue chairs in front.",
        objects = listOf(ObjectSummary("chair", 3, ColorName.BLUE, 0f), ObjectSummary("laptop", 1, null, 90f)),
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun insertsScanWithItsObjects() = runBlocking {
        val id = db.scans().insertScanWithObjects(ScanRecords.scanEntity(result, Lang.EN), ScanRecords.objectEntities(result))
        val scans = db.scans().observeScans().first()
        assertEquals(listOf(id), scans.map { it.id })
        assertEquals("Around you: 3 blue chairs in front.", scans.single().summaryText)
        val objects = db.scans().objectsOf(id)
        assertEquals(listOf("chair", "laptop"), objects.map { it.label })
        assertTrue(objects.all { it.scanId == id })
    }

    @Test
    fun deletingAScanDeletesItsObjects() = runBlocking {
        val id = db.scans().insertScanWithObjects(ScanRecords.scanEntity(result, Lang.KO), ScanRecords.objectEntities(result))
        db.scans().deleteScan(id)
        assertNull(db.scans().getScan(id))
        assertTrue(db.scans().objectsOf(id).isEmpty())
    }
}
