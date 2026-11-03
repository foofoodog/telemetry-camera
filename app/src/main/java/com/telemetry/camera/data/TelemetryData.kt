package com.telemetry.camera.data

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class TelemetryData(
    val elevationDegrees: Float = 0f,
    val headingDegrees: Float = 0f,
    val rollDegrees: Float = 0f,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeMeters: Double? = null,
    val accuracyMeters: Float? = null,
    val pressureHpa: Float? = null,
    val temperatureCelsius: Float? = null,
    val speedMps: Float? = null,
    val bearingDegrees: Float? = null,
    val declination: Float = 0f,
    val isTrueNorth: Boolean = false,
    val sunAzimuth: Float? = null,
    val sunElevation: Float? = null,
    val moonAzimuth: Float? = null,
    val moonElevation: Float? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isBurned: Boolean = false
) {
    val effectiveHeading: Float
        get() = if (isTrueNorth) {
            ((headingDegrees + declination) % 360f + 360f) % 360f
        } else {
            headingDegrees
        }

    val cardinalDirection: String
        get() = getCardinal(effectiveHeading)

    fun formattedElevation(): String {
        val sign = if (elevationDegrees >= 0f) "+" else ""
        return String.format(Locale.US, "%s%.1f°", sign, elevationDegrees)
    }

    fun formattedHeading(): String {
        val refTag = if (isTrueNorth) " T" else ""
        return String.format(Locale.US, "%03.0f° %s%s", effectiveHeading, cardinalDirection, refTag)
    }

    fun formattedHeadingWithMode(mode: CompassDisplayMode): String {
        val refTag = if (isTrueNorth) " T" else ""
        return when (mode) {
            CompassDisplayMode.DEGREES_AND_CARDINAL -> formattedHeading()
            CompassDisplayMode.DEGREES_ONLY -> String.format(Locale.US, "%03.0f°%s", effectiveHeading, refTag)
            CompassDisplayMode.CARDINAL_ONLY -> "$cardinalDirection$refTag"
            CompassDisplayMode.NONE -> ""
        }
    }

    fun formattedRoll(): String {
        val sign = if (rollDegrees >= 0f) "+" else ""
        return String.format(Locale.US, "%s%.1f°", sign, rollDegrees)
    }

    fun formattedDateTime(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return sdf.format(Date(timestamp))
    }

    fun formattedDateOnly(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date(timestamp))
    }

    fun formattedTimeOnly(): String {
        val sdf = SimpleDateFormat("HH:mm:ss z", Locale.US)
        return sdf.format(Date(timestamp))
    }

    fun formattedCoordinatesDecimal(): String {
        if (latitude == null || longitude == null) return "GPS: Acquiring..."
        val latDir = if (latitude >= 0) "N" else "S"
        val lonDir = if (longitude >= 0) "E" else "W"
        return String.format(
            Locale.US,
            "%.5f° %s, %.5f° %s",
            abs(latitude), latDir,
            abs(longitude), lonDir
        )
    }

    fun formattedCoordinatesDms(): String {
        if (latitude == null || longitude == null) return "GPS: Acquiring..."
        return "${toDms(latitude, true)}, ${toDms(longitude, false)}"
    }

    fun formattedAltitude(unitSystem: UnitSystem = UnitSystem.IMPERIAL): String {
        if (altitudeMeters == null) return "Alt: --"
        return if (unitSystem == UnitSystem.METRIC) {
            String.format(Locale.US, "Alt: %.0fm", altitudeMeters)
        } else {
            val feet = altitudeMeters * 3.28084
            String.format(Locale.US, "Alt: %.0fft", feet)
        }
    }

    fun formattedAccuracy(unitSystem: UnitSystem = UnitSystem.IMPERIAL): String {
        val acc = accuracyMeters ?: return ""
        return if (unitSystem == UnitSystem.METRIC) {
            String.format(Locale.US, " (±%.1fm)", acc)
        } else {
            val accFt = acc * 3.28084f
            String.format(Locale.US, " (±%.0fft)", accFt)
        }
    }

    fun formattedPressure(unitSystem: UnitSystem = UnitSystem.IMPERIAL): String {
        val hpa = pressureHpa ?: return "Baro: --"
        if (hpa.isNaN() || hpa.isInfinite() || hpa !in 300f..1200f) return "Baro: --"
        return if (unitSystem == UnitSystem.METRIC) {
            String.format(Locale.US, "%.1f hPa", hpa)
        } else {
            val inHg = hpa * 0.02953f
            String.format(Locale.US, "%.2f inHg", inHg)
        }
    }

    fun formattedTemperature(unitSystem: UnitSystem = UnitSystem.IMPERIAL): String {
        val c = temperatureCelsius ?: return "Temp: --"
        if (c.isNaN() || c.isInfinite() || c !in -70f..100f) return "Temp: --"
        return if (unitSystem == UnitSystem.METRIC) {
            String.format(Locale.US, "%.1f°C", c)
        } else {
            val f = c * 1.8f + 32f
            String.format(Locale.US, "%.1f°F", f)
        }
    }

    fun formattedSpeed(unitSystem: UnitSystem = UnitSystem.IMPERIAL): String {
        val mps = speedMps ?: return if (unitSystem == UnitSystem.METRIC) "0.0 km/h" else "0.0 mph"
        return if (unitSystem == UnitSystem.METRIC) {
            val kmh = mps * 3.6f
            String.format(Locale.US, "%.1f km/h", kmh)
        } else {
            val mph = mps * 2.23694f
            String.format(Locale.US, "%.1f mph", mph)
        }
    }

    fun formattedBearing(): String {
        val bearing = bearingDegrees ?: return "--"
        val cardinal = getCardinal(bearing)
        return String.format(Locale.US, "%03.0f° %s", bearing, cardinal)
    }

    fun formattedSpeedAndBearing(unitSystem: UnitSystem = UnitSystem.IMPERIAL): String {
        val speedStr = formattedSpeed(unitSystem)
        val bearingStr = formattedBearing()
        return "$speedStr | Track: $bearingStr"
    }

    fun toJson(): String {
        val json = JSONObject()
        json.put("elevation", elevationDegrees.toDouble())
        json.put("heading", headingDegrees.toDouble())
        json.put("roll", rollDegrees.toDouble())
        latitude?.let { json.put("lat", it) }
        longitude?.let { json.put("lon", it) }
        altitudeMeters?.let { json.put("alt", it) }
        accuracyMeters?.let { json.put("acc", it.toDouble()) }
        pressureHpa?.let { json.put("baro", it.toDouble()) }
        temperatureCelsius?.let { json.put("temp", it.toDouble()) }
        speedMps?.let { json.put("speed", it.toDouble()) }
        bearingDegrees?.let { json.put("bearing", it.toDouble()) }
        if (declination != 0f) json.put("declination", declination.toDouble())
        if (isTrueNorth) json.put("isTrueNorth", true)
        sunAzimuth?.let { json.put("sunAz", it.toDouble()) }
        sunElevation?.let { json.put("sunEl", it.toDouble()) }
        moonAzimuth?.let { json.put("moonAz", it.toDouble()) }
        moonElevation?.let { json.put("moonEl", it.toDouble()) }
        json.put("timestamp", timestamp)
        json.put("isBurned", isBurned)
        return json.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): TelemetryData? {
            return try {
                val start = jsonStr.indexOf('{')
                val end = jsonStr.lastIndexOf('}')
                val cleanJson = if (start != -1 && end != -1 && end > start) {
                    jsonStr.substring(start, end + 1)
                } else {
                    jsonStr
                }
                val json = JSONObject(cleanJson)
                TelemetryData(
                    elevationDegrees = json.optDouble("elevation", 0.0).toFloat(),
                    headingDegrees = json.optDouble("heading", 0.0).toFloat(),
                    rollDegrees = json.optDouble("roll", 0.0).toFloat(),
                    latitude = if (json.has("lat") && !json.isNull("lat")) json.getDouble("lat") else null,
                    longitude = if (json.has("lon") && !json.isNull("lon")) json.getDouble("lon") else null,
                    altitudeMeters = if (json.has("alt") && !json.isNull("alt")) json.getDouble("alt") else null,
                    accuracyMeters = if (json.has("acc") && !json.isNull("acc")) json.getDouble("acc").toFloat() else null,
                    pressureHpa = if (json.has("baro") && !json.isNull("baro")) json.getDouble("baro").toFloat() else null,
                    temperatureCelsius = if (json.has("temp") && !json.isNull("temp")) json.getDouble("temp").toFloat() else null,
                    speedMps = if (json.has("speed") && !json.isNull("speed")) json.getDouble("speed").toFloat() else null,
                    bearingDegrees = if (json.has("bearing") && !json.isNull("bearing")) json.getDouble("bearing").toFloat() else null,
                    declination = json.optDouble("declination", 0.0).toFloat(),
                    isTrueNorth = json.optBoolean("isTrueNorth", false),
                    sunAzimuth = if (json.has("sunAz") && !json.isNull("sunAz")) json.getDouble("sunAz").toFloat() else null,
                    sunElevation = if (json.has("sunEl") && !json.isNull("sunEl")) json.getDouble("sunEl").toFloat() else null,
                    moonAzimuth = if (json.has("moonAz") && !json.isNull("moonAz")) json.getDouble("moonAz").toFloat() else null,
                    moonElevation = if (json.has("moonEl") && !json.isNull("moonEl")) json.getDouble("moonEl").toFloat() else null,
                    timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                    isBurned = json.optBoolean("isBurned", false)
                )
            } catch (e: Exception) {
                null
            }
        }

        private fun getCardinal(azimuthDegrees: Float): String {
            val directions = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
                "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
            val index = (((azimuthDegrees % 360) + 360) % 360 / 22.5f + 0.5f).toInt() % 16
            return directions[index]
        }

        private fun toDms(coordinate: Double, isLatitude: Boolean): String {
            val absCoord = abs(coordinate)
            val degrees = absCoord.toInt()
            val minutesDecimal = (absCoord - degrees) * 60.0
            val minutes = minutesDecimal.toInt()
            val seconds = (minutesDecimal - minutes) * 60.0
            val direction = if (isLatitude) {
                if (coordinate >= 0) "N" else "S"
            } else {
                if (coordinate >= 0) "E" else "W"
            }
            return String.format(Locale.US, "%d°%02d'%04.1f\"%s", degrees, minutes, seconds, direction)
        }
    }
}
