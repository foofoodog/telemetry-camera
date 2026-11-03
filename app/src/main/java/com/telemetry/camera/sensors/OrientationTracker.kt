package com.telemetry.camera.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class OrientationAngles(
    val elevationDegrees: Float = 0f,
    val headingDegrees: Float = 0f,
    val rollDegrees: Float = 0f
)

class OrientationTracker(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val _orientationFlow = MutableStateFlow(OrientationAngles())
    val orientationFlow: StateFlow<OrientationAngles> = _orientationFlow.asStateFlow()

    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val accelReading = FloatArray(3)
    private val magnetReading = FloatArray(3)
    private var hasAccel = false
    private var hasMagnet = false
    private var hasRealRotationVector = false

    // Exponential smoothing state
    private var smoothedElevation = 0f
    private var smoothedRoll = 0f
    private var smoothedHeadingSin = 0f
    private var smoothedHeadingCos = 1f
    private var initialized = false

    private val filterFactor = 0.2f // Smoothing factor

    fun start() {
        // 1. Register rotation vector sensor if available
        rotationVectorSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        // 2. ALWAYS register accelerometer for fallback (critical for devices without magnetometers or with dummy rotation vector drivers)
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        // 3. Register magnetometer if hardware exists
        magnetometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        hasAccel = false
        hasMagnet = false
        hasRealRotationVector = false
        initialized = false
    }

    private fun getDisplayRotation(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                context.display?.rotation ?: Surface.ROTATION_0
            } catch (e: Exception) {
                Surface.ROTATION_0
            }
        } else {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            @Suppress("DEPRECATION")
            wm?.defaultDisplay?.rotation ?: Surface.ROTATION_0
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                val v = event.values
                // Guard against dummy sensors on budget tablets that output all zeros
                val normSq = v[0] * v[0] + v[1] * v[1] + v[2] * v[2]
                if (normSq > 0.0001f) {
                    hasRealRotationVector = true
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, v)
                    processMatrix(rotationMatrix)
                }
            }
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelReading, 0, 3)
                hasAccel = true

                // Only use accelerometer fallback if 9-axis rotation vector is not producing real data
                if (!hasRealRotationVector) {
                    if (hasMagnet) {
                        val success = SensorManager.getRotationMatrix(rotationMatrix, null, accelReading, magnetReading)
                        if (success) {
                            processMatrix(rotationMatrix)
                        } else {
                            computeFromAccelOnly(accelReading)
                        }
                    } else {
                        // Tablet/budget device without compass magnetometer: compute Elevation and Roll directly from Accelerometer gravity
                        computeFromAccelOnly(accelReading)
                    }
                }
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magnetReading, 0, 3)
                hasMagnet = true
                if (hasAccel && !hasRealRotationVector) {
                    val success = SensorManager.getRotationMatrix(rotationMatrix, null, accelReading, magnetReading)
                    if (success) {
                        processMatrix(rotationMatrix)
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun processMatrix(matrix: FloatArray) {
        val rotation = getDisplayRotation()
        when (rotation) {
            Surface.ROTATION_90 -> SensorManager.remapCoordinateSystem(
                matrix,
                SensorManager.AXIS_Y,
                SensorManager.AXIS_MINUS_X,
                remappedMatrix
            )
            Surface.ROTATION_180 -> SensorManager.remapCoordinateSystem(
                matrix,
                SensorManager.AXIS_MINUS_X,
                SensorManager.AXIS_MINUS_Y,
                remappedMatrix
            )
            Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(
                matrix,
                SensorManager.AXIS_MINUS_Y,
                SensorManager.AXIS_X,
                remappedMatrix
            )
            else -> System.arraycopy(matrix, 0, remappedMatrix, 0, 9)
        }
        computeAnglesFromMatrix(remappedMatrix)
    }

    private fun computeAnglesFromMatrix(r: FloatArray) {
        // The rear camera looks along the negative Z-axis of the phone: (0, 0, -1).
        // Transformed to Earth coordinates (East, North, Up):
        // d_east  = -r[2]
        // d_north = -r[5]
        // d_up    = -r[8]
        val dEast = -r[2]
        val dNorth = -r[5]
        val dUp = -r[8]

        // 1. Elevation angle (Pitch of the camera line-of-sight relative to horizon)
        val clampedUp = dUp.coerceIn(-1.0f, 1.0f)
        val rawElevation = Math.toDegrees(asin(clampedUp.toDouble())).toFloat()

        // 2. Azimuth / Compass Heading
        val rawAzimuthRad = atan2(dEast.toDouble(), dNorth.toDouble())
        var rawHeading = Math.toDegrees(rawAzimuthRad).toFloat()
        rawHeading = (rawHeading + 360f) % 360f

        // 3. Roll angle (Tilt sideways around camera optical axis)
        val rawRoll = Math.toDegrees(atan2(r[6].toDouble(), r[7].toDouble())).toFloat()

        emitSmoothedAngles(rawElevation, rawHeading, rawRoll, hasValidHeading = true)
    }

    private fun computeFromAccelOnly(a: FloatArray) {
        val ax = a[0]
        val ay = a[1]
        val az = a[2]
        val g = sqrt(ax * ax + ay * ay + az * az)
        if (g < 0.1f) return

        // Remap accelerometer vector based on current display orientation
        val rotation = getDisplayRotation()
        val rx: Float
        val ry: Float
        when (rotation) {
            Surface.ROTATION_90 -> {
                rx = -ay
                ry = ax
            }
            Surface.ROTATION_180 -> {
                rx = -ax
                ry = -ay
            }
            Surface.ROTATION_270 -> {
                rx = ay
                ry = -ax
            }
            else -> {
                rx = ax
                ry = ay
            }
        }

        // Camera looks along -Z. Elevation sine is az / g.
        val sinElevation = (az / g).coerceIn(-1.0f, 1.0f)
        val rawElevation = Math.toDegrees(asin(sinElevation.toDouble())).toFloat()

        // Roll is angle around optical axis
        val rawRoll = Math.toDegrees(atan2(-rx.toDouble(), ry.toDouble())).toFloat()

        emitSmoothedAngles(rawElevation, 0f, rawRoll, hasValidHeading = false)
    }

    private fun emitSmoothedAngles(
        rawElevation: Float,
        rawHeading: Float,
        rawRoll: Float,
        hasValidHeading: Boolean
    ) {
        if (!initialized) {
            smoothedElevation = rawElevation
            smoothedRoll = rawRoll
            val rad = Math.toRadians(rawHeading.toDouble())
            smoothedHeadingSin = sin(rad).toFloat()
            smoothedHeadingCos = cos(rad).toFloat()
            initialized = true
        } else {
            smoothedElevation += filterFactor * (rawElevation - smoothedElevation)
            smoothedRoll += filterFactor * (rawRoll - smoothedRoll)

            if (hasValidHeading) {
                val rad = Math.toRadians(rawHeading.toDouble())
                val targetSin = sin(rad).toFloat()
                val targetCos = cos(rad).toFloat()
                smoothedHeadingSin += filterFactor * (targetSin - smoothedHeadingSin)
                smoothedHeadingCos += filterFactor * (targetCos - smoothedHeadingCos)
            }
        }

        val finalHeading = if (hasValidHeading) {
            val h = Math.toDegrees(atan2(smoothedHeadingSin.toDouble(), smoothedHeadingCos.toDouble())).toFloat()
            (h + 360f) % 360f
        } else {
            0f
        }

        _orientationFlow.value = OrientationAngles(
            elevationDegrees = smoothedElevation,
            headingDegrees = finalHeading,
            rollDegrees = smoothedRoll
        )
    }
}
