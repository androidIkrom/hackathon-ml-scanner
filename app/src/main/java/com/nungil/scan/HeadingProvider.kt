package com.nungil.scan

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.nungil.core.scan.AngleMath
import com.nungil.core.scan.HeadingFilter

/**
 * Compass heading of the back camera for an upright phone, from the rotation-vector sensor.
 * remapCoordinateSystem(AXIS_X, AXIS_Z) makes the azimuth describe where the camera points, not the
 * top of the phone. [headingDeg] is null when the phone has no rotation-vector sensor.
 */
class HeadingProvider(context: Context) : SensorEventListener {
    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val filter = HeadingFilter()
    private val rotation = FloatArray(9)
    private val remapped = FloatArray(9)
    private val orientation = FloatArray(3)

    /** Latest smoothed heading; written on the main thread, read on the analysis thread. */
    @Volatile
    var headingDeg: Float? = null
        private set

    val available: Boolean get() = sensor != null

    fun start() {
        sensor?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        sensors.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        SensorManager.remapCoordinateSystem(rotation, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped)
        SensorManager.getOrientation(remapped, orientation)
        val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
        headingDeg = filter.update(AngleMath.normalize(azimuth))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
