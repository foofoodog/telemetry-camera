package com.telemetry.camera.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class SurveyPoint(
    val id: Long,
    val geoPoint: GeoPoint,
    val title: String,
    val telemetry: TelemetryData? = null,
    val photoUri: Uri? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class SurveyTrack(
    val title: String,
    val points: List<GeoPoint>,
    val waypoints: List<SurveyPoint>,
    val totalDistanceMeters: Double,
    val boundingBox: BoundingBox?
)

object SurveyPathParser {

    /**
     * Builds a SurveyTrack directly from a list of GalleryPhoto items if telemetry is already present.
     */
    fun fromGalleryPhotos(photos: List<GalleryPhoto>): SurveyTrack {
        val validPoints = photos.mapNotNull { photo ->
            val t = photo.telemetry ?: return@mapNotNull null
            val lat = t.latitude ?: return@mapNotNull null
            val lon = t.longitude ?: return@mapNotNull null
            SurveyPoint(
                id = photo.id,
                geoPoint = GeoPoint(lat, lon, t.altitudeMeters ?: 0.0),
                title = photo.displayName,
                telemetry = t,
                photoUri = photo.uri,
                timestamp = t.timestamp
            )
        }.sortedBy { it.timestamp }

        val geoPoints = validPoints.map { it.geoPoint }
        val dist = calculatePathDistanceMeters(geoPoints)
        val bbox = computeBoundingBox(geoPoints)

        return SurveyTrack(
            title = "Photo Survey Track",
            points = geoPoints,
            waypoints = validPoints,
            totalDistanceMeters = dist,
            boundingBox = bbox
        )
    }

    /**
     * Asynchronously loads telemetry from EXIF (or cache) for any photo missing it and produces a SurveyTrack.
     */
    suspend fun loadTrackFromPhotos(
        context: Context,
        photos: List<GalleryPhoto>,
        cache: Map<Uri, TelemetryData?> = emptyMap()
    ): SurveyTrack = withContext(Dispatchers.IO) {
        val validPoints = photos.mapNotNull { photo ->
            val t = photo.telemetry ?: cache[photo.uri] ?: run {
                try {
                    val stream = if (photo.uri.scheme == "file") {
                        photo.uri.path?.let { java.io.FileInputStream(it) }
                    } else {
                        context.contentResolver.openInputStream(photo.uri)
                    }
                    stream?.use { ExifTelemetryHelper.readTelemetryFromInputStream(it) }
                } catch (e: Exception) {
                    null
                }
            } ?: return@mapNotNull null

            val lat = t.latitude ?: return@mapNotNull null
            val lon = t.longitude ?: return@mapNotNull null
            SurveyPoint(
                id = photo.id,
                geoPoint = GeoPoint(lat, lon, t.altitudeMeters ?: 0.0),
                title = photo.displayName,
                telemetry = t,
                photoUri = photo.uri,
                timestamp = t.timestamp
            )
        }.sortedBy { it.timestamp }

        val geoPoints = validPoints.map { it.geoPoint }
        val dist = calculatePathDistanceMeters(geoPoints)
        val bbox = computeBoundingBox(geoPoints)

        SurveyTrack(
            title = "Photo Survey Track",
            points = geoPoints,
            waypoints = validPoints,
            totalDistanceMeters = dist,
            boundingBox = bbox
        )
    }

    /**
     * Parses a GPX, KML, or KMZ file from a content Uri.
     */
    suspend fun parseFromUri(context: Context, uri: Uri): SurveyTrack? = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        val mimeType = contentResolver.getType(uri) ?: ""
        val fileName = uri.lastPathSegment?.lowercase() ?: ""

        try {
            when {
                fileName.endsWith(".kmz") || mimeType.contains("kmz") || mimeType.contains("zip") -> {
                    contentResolver.openInputStream(uri)?.use { parseKmz(it) }
                }
                fileName.endsWith(".kml") || mimeType.contains("kml") -> {
                    contentResolver.openInputStream(uri)?.use { parseKml(it) }
                }
                fileName.endsWith(".gpx") || mimeType.contains("gpx") -> {
                    contentResolver.openInputStream(uri)?.use { parseGpx(it) }
                }
                else -> {
                    // Try GPX first, then KML
                    contentResolver.openInputStream(uri)?.use { parseGpx(it) }
                        ?: contentResolver.openInputStream(uri)?.use { parseKml(it) }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Parses an uncompressed KML input stream.
     */
    fun parseKml(inputStream: InputStream): SurveyTrack {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(inputStream, "UTF-8")

        val trackPoints = mutableListOf<GeoPoint>()
        val waypoints = mutableListOf<SurveyPoint>()

        var eventType = parser.eventType
        var currentTag = ""
        var inPlacemark = false
        var placemarkName = ""
        var idCounter = 1L

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    if (currentTag.equals("Placemark", ignoreCase = true)) {
                        inPlacemark = true
                        placemarkName = "Survey Point"
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text.trim()
                    if (text.isNotEmpty()) {
                        if (inPlacemark && currentTag.equals("name", ignoreCase = true)) {
                            placemarkName = text
                        } else if (currentTag.equals("coordinates", ignoreCase = true)) {
                            // Format: lon,lat,alt lon,lat,alt ...
                            val coordGroups = text.split("\\s+".toRegex())
                            for (group in coordGroups) {
                                val parts = group.split(",")
                                if (parts.size >= 2) {
                                    val lon = parts[0].toDoubleOrNull()
                                    val lat = parts[1].toDoubleOrNull()
                                    val alt = if (parts.size > 2) parts[2].toDoubleOrNull() ?: 0.0 else 0.0
                                    if (lat != null && lon != null) {
                                        val gp = GeoPoint(lat, lon, alt)
                                        trackPoints.add(gp)
                                        if (inPlacemark) {
                                            waypoints.add(
                                                SurveyPoint(
                                                    id = idCounter++,
                                                    geoPoint = gp,
                                                    title = placemarkName
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("Placemark", ignoreCase = true)) {
                        inPlacemark = false
                    }
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }

        return SurveyTrack(
            title = "Imported KML Survey",
            points = trackPoints,
            waypoints = waypoints,
            totalDistanceMeters = calculatePathDistanceMeters(trackPoints),
            boundingBox = computeBoundingBox(trackPoints)
        )
    }

    /**
     * Parses a KMZ archive by locating and parsing the internal doc.kml file.
     */
    fun parseKmz(inputStream: InputStream): SurveyTrack? {
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name.lowercase().endsWith(".kml")) {
                    return parseKml(zis)
                }
                entry = zis.nextEntry
            }
        }
        return null
    }

    /**
     * Parses a GPX (GPS Exchange Format) input stream.
     */
    fun parseGpx(inputStream: InputStream): SurveyTrack {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(inputStream, "UTF-8")

        val trackPoints = mutableListOf<GeoPoint>()
        val waypoints = mutableListOf<SurveyPoint>()

        var eventType = parser.eventType
        var currentTag = ""
        var inWpt = false
        var inTrkpt = false
        var currentLat = 0.0
        var currentLon = 0.0
        var currentEle = 0.0
        var currentName = ""
        var idCounter = 1L

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    when {
                        currentTag.equals("wpt", ignoreCase = true) -> {
                            inWpt = true
                            currentLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                            currentLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                            currentEle = 0.0
                            currentName = "Waypoint"
                        }
                        currentTag.equals("trkpt", ignoreCase = true) -> {
                            inTrkpt = true
                            currentLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                            currentLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                            currentEle = 0.0
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text.trim()
                    if (text.isNotEmpty()) {
                        if (currentTag.equals("ele", ignoreCase = true)) {
                            currentEle = text.toDoubleOrNull() ?: 0.0
                        } else if (currentTag.equals("name", ignoreCase = true) && inWpt) {
                            currentName = text
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when {
                        parser.name.equals("wpt", ignoreCase = true) -> {
                            inWpt = false
                            val gp = GeoPoint(currentLat, currentLon, currentEle)
                            trackPoints.add(gp)
                            waypoints.add(
                                SurveyPoint(
                                    id = idCounter++,
                                    geoPoint = gp,
                                    title = currentName
                                )
                            )
                        }
                        parser.name.equals("trkpt", ignoreCase = true) -> {
                            inTrkpt = false
                            trackPoints.add(GeoPoint(currentLat, currentLon, currentEle))
                        }
                    }
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }

        return SurveyTrack(
            title = "Imported GPX Survey Track",
            points = trackPoints,
            waypoints = waypoints,
            totalDistanceMeters = calculatePathDistanceMeters(trackPoints),
            boundingBox = computeBoundingBox(trackPoints)
        )
    }

    /**
     * Calculates the cumulative geodesic path distance in meters (Haversine formula).
     */
    fun calculatePathDistanceMeters(points: List<GeoPoint>): Double {
        if (points.size < 2) return 0.0
        var totalDist = 0.0
        for (i in 0 until points.size - 1) {
            totalDist += haversineDistanceMeters(points[i], points[i + 1])
        }
        return totalDist
    }

    private fun haversineDistanceMeters(p1: GeoPoint, p2: GeoPoint): Double {
        val r = 6371000.0 // Earth radius in meters
        val lat1 = Math.toRadians(p1.latitude)
        val lat2 = Math.toRadians(p2.latitude)
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1) * cos(lat2) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    /**
     * Computes the bounding box encompassing all points with a slight margin.
     */
    fun computeBoundingBox(points: List<GeoPoint>): BoundingBox? {
        if (points.isEmpty()) return null
        var minLat = 90.0
        var maxLat = -90.0
        var minLon = 180.0
        var maxLon = -180.0

        for (p in points) {
            if (p.latitude < minLat) minLat = p.latitude
            if (p.latitude > maxLat) maxLat = p.latitude
            if (p.longitude < minLon) minLon = p.longitude
            if (p.longitude > maxLon) maxLon = p.longitude
        }

        // Add 5% padding
        val latSpan = maxLat - minLat
        val lonSpan = maxLon - minLon
        val padLat = if (latSpan == 0.0) 0.01 else latSpan * 0.1
        val padLon = if (lonSpan == 0.0) 0.01 else lonSpan * 0.1

        return BoundingBox(
            (maxLat + padLat).coerceAtMost(85.0),
            (maxLon + padLon).coerceAtMost(180.0),
            (minLat - padLat).coerceAtLeast(-85.0),
            (minLon - padLon).coerceAtLeast(-180.0)
        )
    }
}
