package com.nungil.scan

import com.nungil.contract.Lang
import com.nungil.core.scan.AngleMath
import com.nungil.core.scan.ScanResult
import com.nungil.data.DetectedObjectEntity
import com.nungil.data.ScanEntity

/** Turns a finished scan into the Room rows that the History screen (I) shows. */
object ScanRecords {
    fun scanEntity(result: ScanResult, lang: Lang) = ScanEntity(
        startedAt = result.startedAtMs,
        mode = result.mode.name,
        coveragePercent = result.coveragePercent,
        summaryText = result.summary,
        lang = lang.tag,
    )

    fun objectEntities(result: ScanResult): List<DetectedObjectEntity> = result.objects.map {
        DetectedObjectEntity(
            label = it.label,
            count = it.count,
            colorName = it.color?.name,
            relAngleDeg = it.angle,
            sector8 = AngleMath.sector8(it.angle),
        )
    }
}
