package com.nungil.people

import android.graphics.Bitmap
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.Closeable
import java.util.concurrent.TimeUnit

/** ML Kit face detection (bundled model, works offline). Worker thread only: it blocks. */
class FaceFinder : Closeable {
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setMinFaceSize(MIN_FACE_SIZE)
            .build(),
    )

    /** Faces in an upright bitmap; empty on any error or after 2 s. */
    fun find(bitmap: Bitmap): List<Face> = try {
        Tasks.await(detector.process(InputImage.fromBitmap(bitmap, 0)), 2, TimeUnit.SECONDS)
    } catch (e: Exception) {
        Log.i(TAG, "Face detection failed: ${e.message}")
        emptyList()
    }

    override fun close() = detector.close()

    private companion object {
        const val TAG = "Nungil"
        const val MIN_FACE_SIZE = 0.1f
    }
}
