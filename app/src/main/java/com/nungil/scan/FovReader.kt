package com.nungil.scan

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import com.nungil.contract.Facing
import com.nungil.core.scan.FovMath

/** Reads the horizontal field of view of the first camera facing [Facing] from Camera2 characteristics. */
object FovReader {
    fun read(context: Context, facing: Facing): Float = try {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val wanted = if (facing == Facing.FRONT) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
        val id = manager.cameraIdList.firstOrNull {
            manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == wanted
        }
        if (id == null) {
            FovMath.FALLBACK_DEG
        } else {
            val c = manager.getCameraCharacteristics(id)
            val size = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            val focal = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
            val orientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            FovMath.horizontalFovDeg(size?.width ?: 0f, size?.height ?: 0f, focal ?: 0f, orientation)
        }
    } catch (e: Exception) {
        Log.i("Nungil", "Field of view unavailable, using ${FovMath.FALLBACK_DEG}: ${e.message}")
        FovMath.FALLBACK_DEG
    }
}
