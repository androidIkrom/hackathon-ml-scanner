package com.nungil.people

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import com.nungil.core.people.FaceGeometry

/** Cuts a face out of a frame: 10% margin, levelled by the eye line when it is tilted 3° or more. */
object FaceCrops {
    /** null when the crop would be under 24 px. */
    fun crop(frame: Bitmap, face: Face): Bitmap? {
        val box = face.boundingBox
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
        val angle = if (leftEye != null && rightEye != null) {
            FaceGeometry.eyeAngleDeg(leftEye.x, leftEye.y, rightEye.x, rightEye.y)
        } else {
            0f
        }
        if (!FaceGeometry.needsLeveling(angle)) {
            return cut(frame, FaceGeometry.expand(box.left, box.top, box.right, box.bottom, FaceGeometry.CROP_MARGIN, frame.width, frame.height))
        }
        // Take a wider crop, rotate it level around its centre, then cut the face out of the rotated picture.
        val wide = FaceGeometry.expand(box.left, box.top, box.right, box.bottom, FaceGeometry.ALIGNED_MARGIN, frame.width, frame.height)
        val wideBitmap = cut(frame, wide) ?: return null
        val matrix = Matrix().apply { setRotate(-angle, wideBitmap.width / 2f, wideBitmap.height / 2f) }
        val rotated = Bitmap.createBitmap(wideBitmap, 0, 0, wideBitmap.width, wideBitmap.height, matrix, true)
        val bounds = RectF(0f, 0f, wideBitmap.width.toFloat(), wideBitmap.height.toFloat()).also { matrix.mapRect(it) }
        val centre = floatArrayOf(box.exactCenterX() - wide[0], box.exactCenterY() - wide[1]).also { matrix.mapPoints(it) }
        val rect = FaceGeometry.around(
            centre[0] - bounds.left,
            centre[1] - bounds.top,
            box.width() * (0.5f + FaceGeometry.CROP_MARGIN),
            box.height() * (0.5f + FaceGeometry.CROP_MARGIN),
            rotated.width,
            rotated.height,
        )
        return cut(rotated, rect)
    }

    fun mirror(bitmap: Bitmap): Bitmap {
        val matrix = Matrix().apply { preScale(-1f, 1f) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun cut(source: Bitmap, rect: IntArray): Bitmap? {
        if (!FaceGeometry.bigEnough(rect)) return null
        return Bitmap.createBitmap(source, rect[0], rect[1], rect[2] - rect[0], rect[3] - rect[1])
    }
}
