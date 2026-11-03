package com.telemetry.camera.data

import android.location.Location
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

object ExifTelemetryHelper {

    fun writeTelemetryToExif(
        file: File,
        telemetry: TelemetryData,
        orientation: Int? = null,
        unitSystem: UnitSystem = UnitSystem.IMPERIAL
    ) {
        val exif = ExifInterface(file.absolutePath)

        // 1. Write structured JSON to UserComment for exact 100% telemetry restoration
        exif.setAttribute(ExifInterface.TAG_USER_COMMENT, telemetry.toJson())

        // 2. Comprehensive human-readable Image Description with all raw metrics formatted to unit system
        val accStr = telemetry.formattedAccuracy(unitSystem)
        val latStr = telemetry.latitude?.let { String.format(Locale.US, "%.6f", it) } ?: "--"
        val lonStr = telemetry.longitude?.let { String.format(Locale.US, "%.6f", it) } ?: "--"
        val baroStr = telemetry.formattedPressure(unitSystem)
        val tempStr = telemetry.formattedTemperature(unitSystem)
        val speedStr = telemetry.formattedSpeed(unitSystem)
        val trackStr = telemetry.bearingDegrees?.let { String.format(Locale.US, "%.0f°", it) } ?: "--"
        exif.setAttribute(
            ExifInterface.TAG_IMAGE_DESCRIPTION,
            "Elevation: ${telemetry.formattedElevation()}, Heading: ${telemetry.formattedHeading()}, Roll: ${telemetry.formattedRoll()}, Lat: $latStr, Lon: $lonStr, Alt: ${telemetry.formattedAltitude(unitSystem)}, Acc: $accStr, Baro: $baroStr, Temp: $tempStr, Spd: $speedStr, Trk: $trackStr"
        )

        // 3. Standard EXIF Date & Time
        val date = Date(telemetry.timestamp)
        val sdfLocal = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
        val formattedDate = sdfLocal.format(date)
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, formattedDate)
        exif.setAttribute(ExifInterface.TAG_DATETIME, formattedDate)
        exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, formattedDate)

        // 4. Direction of Image / Heading (always write, regardless of GPS lock)
        val normalizedHeading = ((telemetry.headingDegrees % 360f + 360f) % 360f)
        val headingInt = (normalizedHeading * 100).toInt()
        exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION, "$headingInt/100")
        exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION_REF, "M") // Magnetic

        // 5. Standard EXIF GPS Tags
        if (telemetry.latitude != null && telemetry.longitude != null) {
            val loc = Location("telemetry").apply {
                latitude = telemetry.latitude
                longitude = telemetry.longitude
                telemetry.altitudeMeters?.let { altitude = it }
                telemetry.accuracyMeters?.let { accuracy = it }
                time = telemetry.timestamp
            }
            exif.setGpsInfo(loc)

            // Re-apply TAG_GPS_IMG_DIRECTION because setGpsInfo resets GPS attributes
            exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION, "$headingInt/100")
            exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION_REF, "M")

            val sdfGpsDate = SimpleDateFormat("yyyy:MM:dd", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val sdfGpsTime = SimpleDateFormat("HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, sdfGpsDate.format(date))
            exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, sdfGpsTime.format(date))

            telemetry.accuracyMeters?.let { acc ->
                val accInt = (abs(acc) * 100).toInt()
                exif.setAttribute(ExifInterface.TAG_GPS_H_POSITIONING_ERROR, "$accInt/100")
            }

            // GPS Speed & Track
            telemetry.speedMps?.let { speed ->
                val speedKmhInt = (speed * 3.6f * 100).toInt()
                exif.setAttribute(ExifInterface.TAG_GPS_SPEED, "$speedKmhInt/100")
                exif.setAttribute(ExifInterface.TAG_GPS_SPEED_REF, "K")
            }
            telemetry.bearingDegrees?.let { bearing ->
                val trackInt = (((bearing % 360f + 360f) % 360f) * 100).toInt()
                exif.setAttribute(ExifInterface.TAG_GPS_TRACK, "$trackInt/100")
                exif.setAttribute(ExifInterface.TAG_GPS_TRACK_REF, "T")
            }
        } else if (telemetry.altitudeMeters != null) {
            val altVal = abs(telemetry.altitudeMeters)
            val altInt = (altVal * 100).toInt()
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, "$altInt/100")
            exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, if (telemetry.altitudeMeters >= 0) "0" else "1")
        }

        // 6. Camera / Software Info & Orientation
        exif.setAttribute(ExifInterface.TAG_SOFTWARE, "TelemetryCamera 1.0")
        if (orientation != null) {
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
        }

        exif.saveAttributes()
    }

    fun readTelemetryFromExif(file: File): TelemetryData? {
        return try {
            val exif = ExifInterface(file.absolutePath)
            readFromExifInterface(exif)
        } catch (e: Exception) {
            null
        }
    }

    fun readTelemetryFromInputStream(inputStream: InputStream): TelemetryData? {
        return try {
            val exif = ExifInterface(inputStream)
            readFromExifInterface(exif)
        } catch (e: Exception) {
            null
        }
    }

    private fun readFromExifInterface(exif: ExifInterface): TelemetryData? {
        // Priority 1: Check UserComment JSON payload
        val userComment = exif.getAttribute(ExifInterface.TAG_USER_COMMENT)
        if (!userComment.isNullOrBlank()) {
            val fromJson = TelemetryData.fromJson(userComment)
            if (fromJson != null) return fromJson
        }

        // Priority 2: Fallback to standard EXIF tags and description
        val latLong = exif.latLong
        val latitude = latLong?.get(0)
        val longitude = latLong?.get(1)
        val altitude = exif.getAltitude(0.0).takeIf { it != 0.0 }

        val directionStr = exif.getAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION)
        var heading = directionStr?.let { parseRational(it) } ?: 0f

        val accuracyStr = exif.getAttribute(ExifInterface.TAG_GPS_H_POSITIONING_ERROR)
        val accuracy = accuracyStr?.let { parseRational(it) }

        val speedExifStr = exif.getAttribute(ExifInterface.TAG_GPS_SPEED)
        val speedKmh = speedExifStr?.let { parseRational(it) }
        val speedMps = speedKmh?.let { it / 3.6f }

        val trackExifStr = exif.getAttribute(ExifInterface.TAG_GPS_TRACK)
        val bearing = trackExifStr?.let { parseRational(it) }

        val dateTimeStr = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
        val timestamp = parseExifDate(dateTimeStr) ?: System.currentTimeMillis()

        var elevation = 0f
        var roll = 0f

        // Attempt extracting elevation & roll from ImageDescription if present
        val description = exif.getAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION)
        if (!description.isNullOrBlank()) {
            val elevMatch = Regex("""Elev(?:ation)?:\s*([+-]?\d+(?:\.\d+)?)""").find(description)
            if (elevMatch != null) {
                elevation = elevMatch.groupValues[1].toFloatOrNull() ?: 0f
            }
            val rollMatch = Regex("""Roll:\s*([+-]?\d+(?:\.\d+)?)""").find(description)
            if (rollMatch != null) {
                roll = rollMatch.groupValues[1].toFloatOrNull() ?: 0f
            }
            if (heading == 0f) {
                val headMatch = Regex("""Heading:\s*(\d+(?:\.\d+)?)""").find(description)
                if (headMatch != null) {
                    heading = headMatch.groupValues[1].toFloatOrNull() ?: 0f
                }
            }
        }

        return TelemetryData(
            elevationDegrees = elevation,
            headingDegrees = heading,
            rollDegrees = roll,
            latitude = latitude,
            longitude = longitude,
            altitudeMeters = altitude,
            accuracyMeters = accuracy,
            speedMps = speedMps,
            bearingDegrees = bearing,
            timestamp = timestamp
        )
    }

    private fun parseRational(rationalStr: String): Float {
        return try {
            val parts = rationalStr.split("/")
            if (parts.size == 2) {
                parts[0].trim().toFloat() / parts[1].trim().toFloat()
            } else {
                rationalStr.toFloat()
            }
        } catch (e: Exception) {
            0f
        }
    }

    private fun parseExifDate(dateStr: String?): Long? {
        if (dateStr.isNullOrBlank()) return null
        return try {
            val sdf = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
            sdf.parse(dateStr)?.time
        } catch (e: Exception) {
            null
        }
    }
}
