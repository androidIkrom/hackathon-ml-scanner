package com.nungil.core.scan

import com.nungil.contract.Box
import com.nungil.contract.Detection
import com.nungil.contract.ScanSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class DetectionFilterTest {
    private val box = Box(0.2f, 0.2f, 0.4f, 0.4f)

    @Test fun detectorRunsAtPointThree() = assertEquals(0.3f, DetectionFilter.DETECTOR_THRESHOLD)

    @Test fun defaultSettingNeedsPointSeven() {
        assertEquals(0.7f, ScanSettings.DEFAULT_MIN_SCORE)
        assertEquals(0.7f, DetectionFilter.minScoreFor("chair", 0.7f), 0.0001f)
    }

    @Test fun peopleAreAcceptedPointTwoLower() =
        assertEquals(0.5f, DetectionFilter.minScoreFor("person", 0.7f), 0.0001f)

    @Test fun peopleNeverGoBelowTheDetectorThreshold() =
        assertEquals(0.3f, DetectionFilter.minScoreFor("person", 0.4f), 0.0001f)

    @Test fun keepFiltersPerLabel() {
        val kept = DetectionFilter.keep(
            listOf(
                Detection("chair", 0.69f, box),
                Detection("chair", 0.7f, box),
                Detection("person", 0.5f, box),
                Detection("person", 0.49f, box),
            ),
            0.7f,
        )
        assertEquals(listOf(0.7f to "chair", 0.5f to "person"), kept.map { it.score to it.label })
    }

    @Test fun fixedFloorIgnoresLabels() {
        val kept = DetectionFilter.keepAtLeast(
            listOf(Detection("person", 0.39f, box), Detection("backpack", 0.4f, box)),
            0.4f,
        )
        assertEquals(listOf("backpack"), kept.map { it.label })
    }

    @Test fun bigFurnitureFillingTheViewNeedsMore() {
        // A plain wall was "refrigerator 0.72" filling 59 % of the frame (desk photo), and Live said "a gray bed" at
        // a desk at 0.70-0.72 (Task 5): a big thing filling the view needs a tenth more.
        val wall = Detection("refrigerator", 0.72f, Box(0.0f, 0.12f, 0.67f, 1.0f))
        val bed = Detection("bed", 0.82f, Box(0.0f, 0.1f, 1.0f, 0.9f))
        val smallBed = Detection("bed", 0.72f, Box(0.2f, 0.4f, 0.6f, 0.7f))
        val laptop = Detection("laptop", 0.72f, Box(0.0f, 0.1f, 1.0f, 0.9f))
        assertEquals(listOf(bed, smallBed, laptop), DetectionFilter.keep(listOf(wall, bed, smallBed, laptop), 0.7f))
        assertEquals(0.8f, DetectionFilter.minScoreFor(wall, 0.7f), 0.0001f)
        assertEquals(0.7f, DetectionFilter.minScoreFor(smallBed, 0.7f), 0.0001f)
    }
}
