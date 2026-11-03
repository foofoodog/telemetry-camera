package com.telemetry.camera.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class HudDisplaySize(val label: String, val scaleFactor: Float) {
    SMALL("Small", 0.85f),
    MEDIUM("Medium", 1.0f),
    LARGE("Large", 1.15f)
}

enum class GpsDisplayMode(val label: String) {
    BOTH("Both (Decimal & DMS)"),
    DECIMAL("Decimal Degrees"),
    DMS("Degrees, Minutes, Seconds (DMS)"),
    NONE("Hidden / Off")
}

enum class CompassDisplayMode(val label: String) {
    DEGREES_AND_CARDINAL("Degrees & Cardinal (e.g. 284° WNW)"),
    DEGREES_ONLY("Degrees Only (e.g. 284°)"),
    CARDINAL_ONLY("Cardinal Only (e.g. WNW)"),
    NONE("Hidden / Off")
}

enum class NorthReference(val label: String) {
    MAGNETIC("Magnetic North (Sensor)"),
    TRUE_NORTH("True North (GPS Calibrated)")
}

enum class UnitSystem(val label: String) {
    IMPERIAL("Mile + Fahrenheit (mph, °F, ft, inHg)"),
    METRIC("Kilometer + Celsius (km/h, °C, m, hPa)")
}

data class AppSettings(
    val isHudEnabled: Boolean = true,
    val isBurnOverlayEnabled: Boolean = false,
    val displaySize: HudDisplaySize = HudDisplaySize.MEDIUM,
    val gpsDisplayMode: GpsDisplayMode = GpsDisplayMode.BOTH,
    val compassDisplayMode: CompassDisplayMode = CompassDisplayMode.DEGREES_AND_CARDINAL,
    val northReference: NorthReference = NorthReference.TRUE_NORTH,
    val unitSystem: UnitSystem = UnitSystem.IMPERIAL,
    val showTime: Boolean = true,
    val showAltitude: Boolean = true,
    val showHorizonReticle: Boolean = true,
    val showElevation: Boolean = true,
    val showRoll: Boolean = true,
    val showPressure: Boolean = true,
    val showTemperature: Boolean = true,
    val showSpeed: Boolean = true,
    val showTrackBearing: Boolean = true,
    val showWatermarkBadge: Boolean = true,
    val showSunMoonEphemeris: Boolean = true,
    val showAngularRangingGrid: Boolean = true,
    val rangingGridColor: Long = 0xFFF59E0BL,
    val rangingGridThickness: Float = 2.0f
) {
    val showGpsSpeedBearing: Boolean get() = showSpeed || showTrackBearing
}

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("telemetry_camera_prefs", Context.MODE_PRIVATE)

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<AppSettings> = _settingsFlow.asStateFlow()

    fun updateSettings(newSettings: AppSettings) {
        prefs.edit().apply {
            putBoolean(KEY_HUD_ENABLED, newSettings.isHudEnabled)
            putBoolean(KEY_BURN_ENABLED, newSettings.isBurnOverlayEnabled)
            putString(KEY_DISPLAY_SIZE, newSettings.displaySize.name)
            putString(KEY_GPS_MODE, newSettings.gpsDisplayMode.name)
            putString(KEY_COMPASS_MODE, newSettings.compassDisplayMode.name)
            putString(KEY_NORTH_REFERENCE, newSettings.northReference.name)
            putString(KEY_UNIT_SYSTEM, newSettings.unitSystem.name)
            putBoolean(KEY_SHOW_TIME, newSettings.showTime)
            putBoolean(KEY_SHOW_ALTITUDE, newSettings.showAltitude)
            putBoolean(KEY_SHOW_RETICLE, newSettings.showHorizonReticle)
            putBoolean(KEY_SHOW_ELEVATION, newSettings.showElevation)
            putBoolean(KEY_SHOW_ROLL, newSettings.showRoll)
            putBoolean(KEY_SHOW_PRESSURE, newSettings.showPressure)
            putBoolean(KEY_SHOW_TEMPERATURE, newSettings.showTemperature)
            putBoolean(KEY_SHOW_SPEED, newSettings.showSpeed)
            putBoolean(KEY_SHOW_TRACK_BEARING, newSettings.showTrackBearing)
            putBoolean(KEY_SHOW_SPEED_BEARING, newSettings.showGpsSpeedBearing)
            putBoolean(KEY_SHOW_WATERMARK_BADGE, newSettings.showWatermarkBadge)
            putBoolean(KEY_SHOW_EPHEMERIS, newSettings.showSunMoonEphemeris)
            putBoolean(KEY_SHOW_ANGULAR_GRID, newSettings.showAngularRangingGrid)
            putLong(KEY_RANGING_GRID_COLOR, newSettings.rangingGridColor)
            putFloat(KEY_RANGING_GRID_THICKNESS, newSettings.rangingGridThickness)
            apply()
        }
        _settingsFlow.value = newSettings
    }

    private fun loadSettings(): AppSettings {
        val isHud = prefs.getBoolean(KEY_HUD_ENABLED, true)
        val isBurn = prefs.getBoolean(KEY_BURN_ENABLED, false)
        val sizeStr = prefs.getString(KEY_DISPLAY_SIZE, HudDisplaySize.MEDIUM.name) ?: HudDisplaySize.MEDIUM.name
        val gpsModeStr = prefs.getString(KEY_GPS_MODE, GpsDisplayMode.BOTH.name) ?: GpsDisplayMode.BOTH.name
        val compassModeStr = prefs.getString(KEY_COMPASS_MODE, CompassDisplayMode.DEGREES_AND_CARDINAL.name) ?: CompassDisplayMode.DEGREES_AND_CARDINAL.name
        val northRefStr = prefs.getString(KEY_NORTH_REFERENCE, NorthReference.TRUE_NORTH.name) ?: NorthReference.TRUE_NORTH.name
        val unitSystemStr = prefs.getString(KEY_UNIT_SYSTEM, UnitSystem.IMPERIAL.name) ?: UnitSystem.IMPERIAL.name
        val showTime = prefs.getBoolean(KEY_SHOW_TIME, true)
        val showAltitude = prefs.getBoolean(KEY_SHOW_ALTITUDE, true)
        val showReticle = prefs.getBoolean(KEY_SHOW_RETICLE, true)
        val showElev = prefs.getBoolean(KEY_SHOW_ELEVATION, true)
        val showRoll = prefs.getBoolean(KEY_SHOW_ROLL, true)
        val showBaro = prefs.getBoolean(KEY_SHOW_PRESSURE, true)
        val showTemp = prefs.getBoolean(KEY_SHOW_TEMPERATURE, true)
        val speedBearingFallback = prefs.getBoolean(KEY_SHOW_SPEED_BEARING, true)
        val showSpeed = prefs.getBoolean(KEY_SHOW_SPEED, speedBearingFallback)
        val showTrack = prefs.getBoolean(KEY_SHOW_TRACK_BEARING, speedBearingFallback)
        val showWatermarkBadge = prefs.getBoolean(KEY_SHOW_WATERMARK_BADGE, true)
        val showEphemeris = prefs.getBoolean(KEY_SHOW_EPHEMERIS, true)
        val showAngularGrid = prefs.getBoolean(KEY_SHOW_ANGULAR_GRID, true)

        val displaySize = try {
            HudDisplaySize.valueOf(sizeStr)
        } catch (e: Exception) {
            HudDisplaySize.MEDIUM
        }

        val gpsMode = try {
            GpsDisplayMode.valueOf(gpsModeStr)
        } catch (e: Exception) {
            GpsDisplayMode.BOTH
        }

        val compassMode = try {
            CompassDisplayMode.valueOf(compassModeStr)
        } catch (e: Exception) {
            CompassDisplayMode.DEGREES_AND_CARDINAL
        }

        val northReference = try {
            NorthReference.valueOf(northRefStr)
        } catch (e: Exception) {
            NorthReference.MAGNETIC
        }

        val unitSystem = try {
            UnitSystem.valueOf(unitSystemStr)
        } catch (e: Exception) {
            UnitSystem.IMPERIAL
        }

        val gridColor = prefs.getLong(KEY_RANGING_GRID_COLOR, 0xFFF59E0BL)
        val gridThickness = prefs.getFloat(KEY_RANGING_GRID_THICKNESS, 2.0f)

        return AppSettings(
            isHudEnabled = isHud,
            isBurnOverlayEnabled = isBurn,
            displaySize = displaySize,
            gpsDisplayMode = gpsMode,
            compassDisplayMode = compassMode,
            northReference = northReference,
            unitSystem = unitSystem,
            showTime = showTime,
            showAltitude = showAltitude,
            showHorizonReticle = showReticle,
            showElevation = showElev,
            showRoll = showRoll,
            showPressure = showBaro,
            showTemperature = showTemp,
            showSpeed = showSpeed,
            showTrackBearing = showTrack,
            showWatermarkBadge = showWatermarkBadge,
            showSunMoonEphemeris = showEphemeris,
            showAngularRangingGrid = showAngularGrid,
            rangingGridColor = gridColor,
            rangingGridThickness = gridThickness
        )
    }

    companion object {
        private const val KEY_HUD_ENABLED = "key_hud_enabled"
        private const val KEY_BURN_ENABLED = "key_burn_enabled"
        private const val KEY_DISPLAY_SIZE = "key_display_size"
        private const val KEY_GPS_MODE = "key_gps_mode"
        private const val KEY_COMPASS_MODE = "key_compass_mode"
        private const val KEY_NORTH_REFERENCE = "key_north_reference"
        private const val KEY_UNIT_SYSTEM = "key_unit_system"
        private const val KEY_SHOW_TIME = "key_show_time"
        private const val KEY_SHOW_ALTITUDE = "key_show_altitude"
        private const val KEY_SHOW_RETICLE = "key_show_reticle"
        private const val KEY_SHOW_ELEVATION = "key_show_elevation"
        private const val KEY_SHOW_ROLL = "key_show_roll"
        private const val KEY_SHOW_PRESSURE = "key_show_pressure"
        private const val KEY_SHOW_TEMPERATURE = "key_show_temperature"
        private const val KEY_SHOW_SPEED = "key_show_speed"
        private const val KEY_SHOW_TRACK_BEARING = "key_show_track_bearing"
        private const val KEY_SHOW_SPEED_BEARING = "key_show_speed_bearing"
        private const val KEY_SHOW_WATERMARK_BADGE = "key_show_watermark_badge"
        private const val KEY_SHOW_EPHEMERIS = "key_show_ephemeris"
        private const val KEY_SHOW_ANGULAR_GRID = "key_show_angular_grid"
        private const val KEY_RANGING_GRID_COLOR = "key_ranging_grid_color"
        private const val KEY_RANGING_GRID_THICKNESS = "key_ranging_grid_thickness"
    }
}
