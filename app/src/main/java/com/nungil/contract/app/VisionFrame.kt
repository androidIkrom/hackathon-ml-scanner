package com.nungil.contract.app

import android.graphics.Bitmap
import com.nungil.contract.Detection
import com.nungil.contract.Facing

/**
 * One analysed camera frame, produced by com.nungil.scan.CameraSession (A) on the analysis thread.
 *
 * @param detections detector results above the score floor, boxes in the upright image (0..1).
 * @param bitmap upright ARGB_8888 copy of the frame when CameraSession.Options.keepBitmap is true,
 *   else null; on its side when [rotation] is not 0. A new bitmap per frame; never recycle it, it may still be
 *   used by another worker.
 * @param imageWidth width of the upright image in pixels.
 * @param imageHeight height of the upright image in pixels.
 * @param headingDeg compass heading (0..360) the BACK camera points at; null without a rotation sensor.
 * @param hfovDeg horizontal field of view of the upright image in degrees.
 * @param rotation degrees that turn [bitmap] upright: 0 unless CameraSession.Options.upright is false
 *   (CameraSession.upright(frame) turns it).
 */
class VisionFrame(
    val detections: List<Detection>,
    val bitmap: Bitmap?,
    val imageWidth: Int,
    val imageHeight: Int,
    val facing: Facing,
    val headingDeg: Float?,
    val hfovDeg: Float,
    val timestampMs: Long,
    val inferenceMs: Long,
    val rotation: Int = 0,
)
