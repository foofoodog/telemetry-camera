package com.telemetry.camera.sensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EnvironmentData(
    val pressureHpa: Float? = null,
    val temperatureCelsius: Float? = null
)

class EnvironmentTracker(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val pressureSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private val ambientTempSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)

    private val _environmentFlow = MutableStateFlow(EnvironmentData())
    val environmentFlow: StateFlow<EnvironmentData> = _environmentFlow.asStateFlow()

    val currentData: EnvironmentData
        get() = _environmentFlow.value

    private var currentPressure: Float? = null
    private var currentTemperature: Float? = null

    private fun isValidTemperature(celsius: Float?): Boolean {
        return celsius != null && !celsius.isNaN() && !celsius.isInfinite() && celsius in -70f..100f
    }

    private fun isValidPressure(hpa: Float?): Boolean {
        return hpa != null && !hpa.isNaN() && !hpa.isInfinite() && hpa in 300f..1200f
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val tempInt = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -999)
                if (tempInt != -999) {
                    val celsius = tempInt / 10.0f
                    if (isValidTemperature(celsius)) {
                        // If no dedicated ambient sensor or existing temperature is invalid, use battery/device thermal sensor
                        if (ambientTempSensor == null || !isValidTemperature(currentTemperature)) {
                            currentTemperature = celsius
                            updateFlow()
                        }
                    }
                }
            }
        }
    }

    private var isRegistered = false

    fun start() {
        if (isRegistered) return

        // 1. Barometer
        pressureSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        // 2. Ambient Temperature (if available)
        ambientTempSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        // 3. Battery / Device Temperature BroadcastReceiver
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val initial = context.registerReceiver(batteryReceiver, filter)
            if (initial != null) {
                val tempInt = initial.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -999)
                if (tempInt != -999) {
                    val celsius = tempInt / 10.0f
                    if (isValidTemperature(celsius)) {
                        currentTemperature = celsius
                        updateFlow()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        isRegistered = true
    }

    fun stop() {
        if (!isRegistered) return
        sensorManager.unregisterListener(this)
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            // Receiver not registered
        }
        isRegistered = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_PRESSURE -> {
                val valHpa = event.values.getOrNull(0)
                if (isValidPressure(valHpa)) {
                    currentPressure = valHpa
                    updateFlow()
                }
            }
            Sensor.TYPE_AMBIENT_TEMPERATURE -> {
                val valCelsius = event.values.getOrNull(0)
                if (isValidTemperature(valCelsius)) {
                    currentTemperature = valCelsius
                    updateFlow()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun updateFlow() {
        _environmentFlow.value = EnvironmentData(
            pressureHpa = currentPressure,
            temperatureCelsius = currentTemperature
        )
    }
}
