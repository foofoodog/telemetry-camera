package com.telemetry.camera.sensors

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.*

data class CelestialPosition(
    val azimuth: Float,    // 0°..360° from North
    val elevation: Float   // -90°..+90° from Horizon
)

object EphemerisCalculator {

    /**
     * Calculates the approximate position of the Sun (Azimuth and Elevation).
     * Accuracy: ~0.1°-0.3°, well within camera HUD field-of-view needs.
     */
    fun calculateSunPosition(
        latitude: Double,
        longitude: Double,
        timestamp: Long = System.currentTimeMillis()
    ): CelestialPosition {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = timestamp
        }

        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val second = cal.get(Calendar.SECOND)

        // Julian Day calculation
        val d = 367 * year - 7 * (year + (month + 9) / 12) / 4 + 275 * month / 9 + day - 730530 +
                (hour + minute / 60.0 + second / 3600.0) / 24.0

        // Orbital elements of the Sun
        val w = 282.9404 + 4.70935e-5 * d
        val a = 1.000000 // AU
        val e = 0.016709 - 1.151e-9 * d
        val m = (356.0470 + 0.9856002585 * d) % 360.0

        val mRad = Math.toRadians(m)
        // Eccentric anomaly
        val ecc = mRad + e * sin(mRad) * (1.0 + e * cos(mRad))

        // True anomaly and distance
        val xv = cos(ecc) - e
        val yv = sqrt(1.0 - e * e) * sin(ecc)

        val v = Math.toDegrees(atan2(yv, xv))
        val r = sqrt(xv * xv + yv * yv)

        // True longitude
        val lonsun = (v + w) % 360.0
        val lonsunRad = Math.toRadians(lonsun)

        val xs = r * cos(lonsunRad)
        val ys = r * sin(lonsunRad)

        // Ecliptic obliquity
        val oblRad = Math.toRadians(23.4393 - 3.563e-7 * d)

        val xe = xs
        val ye = ys * cos(oblRad)
        val ze = ys * sin(oblRad)

        // Right Ascension and Declination
        val ra = (Math.toDegrees(atan2(ye, xe)) + 360.0) % 360.0
        val dec = Math.toDegrees(atan2(ze, sqrt(xe * xe + ye * ye)))

        // Sidereal time
        val gmst0 = (280.46061837 + 360.98564736629 * d) % 360.0
        val lst = (gmst0 + longitude + 360.0) % 360.0

        // Hour angle
        val ha = (lst - ra + 360.0) % 360.0
        val haRad = Math.toRadians(ha)
        val decRad = Math.toRadians(dec)
        val latRad = Math.toRadians(latitude)

        // Horizontal coordinates
        val sinElev = sin(latRad) * sin(decRad) + cos(latRad) * cos(decRad) * cos(haRad)
        val elevation = Math.toDegrees(asin(sinElev.coerceIn(-1.0, 1.0)))

        val cosAz = (sin(decRad) - sin(latRad) * sinElev) / (cos(latRad) * cos(Math.toRadians(elevation)))
        var azimuth = Math.toDegrees(acos(cosAz.coerceIn(-1.0, 1.0)))
        if (sin(haRad) > 0) {
            azimuth = 360.0 - azimuth
        }

        return CelestialPosition(
            azimuth = azimuth.toFloat(),
            elevation = elevation.toFloat()
        )
    }

    /**
     * Calculates the approximate position of the Moon (Azimuth and Elevation).
     */
    fun calculateMoonPosition(
        latitude: Double,
        longitude: Double,
        timestamp: Long = System.currentTimeMillis()
    ): CelestialPosition {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = timestamp
        }

        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val second = cal.get(Calendar.SECOND)

        val d = 367 * year - 7 * (year + (month + 9) / 12) / 4 + 275 * month / 9 + day - 730530 +
                (hour + minute / 60.0 + second / 3600.0) / 24.0

        // Moon's orbital elements
        val n = (125.1228 - 0.0529538083 * d) % 360.0 // Longitude of ascending node
        val i = 5.1454 // Inclination to ecliptic
        val w = (318.0634 + 0.1643573223 * d) % 360.0 // Argument of periapsis
        val m = (115.3654 + 13.0649929509 * d) % 360.0 // Mean anomaly

        val mRad = Math.toRadians(m)
        val ecc = mRad + 0.0549 * sin(mRad)

        val xv = cos(ecc) - 0.0549
        val yv = sqrt(1.0 - 0.0549 * 0.0549) * sin(ecc)

        val v = Math.toDegrees(atan2(yv, xv))
        val r = sqrt(xv * xv + yv * yv)

        val xh = r * (cos(Math.toRadians(n)) * cos(Math.toRadians(v + w)) - sin(Math.toRadians(n)) * sin(Math.toRadians(v + w)) * cos(Math.toRadians(i)))
        val yh = r * (sin(Math.toRadians(n)) * cos(Math.toRadians(v + w)) + cos(Math.toRadians(n)) * sin(Math.toRadians(v + w)) * cos(Math.toRadians(i)))
        val zh = r * (sin(Math.toRadians(v + w)) * sin(Math.toRadians(i)))

        val lonecl = (Math.toDegrees(atan2(yh, xh)) + 360.0) % 360.0
        val latecl = Math.toDegrees(atan2(zh, sqrt(xh * xh + yh * yh)))

        val oblRad = Math.toRadians(23.4393 - 3.563e-7 * d)
        val loneclRad = Math.toRadians(lonecl)
        val lateclRad = Math.toRadians(latecl)

        val xe = cos(lateclRad) * cos(loneclRad)
        val ye = cos(lateclRad) * sin(loneclRad) * cos(oblRad) - sin(lateclRad) * sin(oblRad)
        val ze = cos(lateclRad) * sin(loneclRad) * sin(oblRad) + sin(lateclRad) * cos(oblRad)

        val ra = (Math.toDegrees(atan2(ye, xe)) + 360.0) % 360.0
        val dec = Math.toDegrees(atan2(ze, sqrt(xe * xe + ye * ye)))

        val gmst0 = (280.46061837 + 360.98564736629 * d) % 360.0
        val lst = (gmst0 + longitude + 360.0) % 360.0
        val ha = (lst - ra + 360.0) % 360.0

        val haRad = Math.toRadians(ha)
        val decRad = Math.toRadians(dec)
        val latRad = Math.toRadians(latitude)

        val sinElev = sin(latRad) * sin(decRad) + cos(latRad) * cos(decRad) * cos(haRad)
        val elevation = Math.toDegrees(asin(sinElev.coerceIn(-1.0, 1.0)))

        val cosAz = (sin(decRad) - sin(latRad) * sinElev) / (cos(latRad) * cos(Math.toRadians(elevation)))
        var azimuth = Math.toDegrees(acos(cosAz.coerceIn(-1.0, 1.0)))
        if (sin(haRad) > 0) {
            azimuth = 360.0 - azimuth
        }

        return CelestialPosition(
            azimuth = azimuth.toFloat(),
            elevation = elevation.toFloat()
        )
    }
}
