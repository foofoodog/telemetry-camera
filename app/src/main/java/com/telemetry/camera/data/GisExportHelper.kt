package com.telemetry.camera.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class KmzExportItem(
    val displayName: String,
    val photoUri: Uri,
    val telemetry: TelemetryData
)

object GisExportHelper {

    /**
     * Exports a formatted companion .json sidecar file for a photo.
     */
    suspend fun exportSidecarJson(
        context: Context,
        displayName: String,
        telemetry: TelemetryData
    ): Uri? = withContext(Dispatchers.IO) {
        val baseName = displayName.substringBeforeLast(".")
        val fileName = "${baseName}.json"
        val content = telemetry.toJson()

        saveTextFileToMediaStore(
            context = context,
            fileName = fileName,
            mimeType = "application/json",
            content = content
        )
    }

    /**
     * Exports a Google Earth KMZ bundle (.kmz) containing all photos, 3D camera viewports,
     * and offline interactive metadata popups.
     */
    suspend fun exportKmz(
        context: Context,
        items: List<KmzExportItem>
    ): Uri? = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext null

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "TELEMETRY_EARTH_${timestamp}.kmz"
        val tempFile = File(context.cacheDir, "temp_${timestamp}.kmz")

        try {
            ZipOutputStream(FileOutputStream(tempFile)).use { zos ->
                val sb = StringBuilder()
                sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append("\n")
                sb.append("""<kml xmlns="http://www.opengis.net/kml/2.2" xmlns:gx="http://www.google.com/kml/ext/2.2">""").append("\n")
                sb.append("  <Document>\n")
                sb.append("    <name>Telemetry Camera 3D Survey (${timestamp})</name>\n")
                sb.append("    <open>1</open>\n")
                sb.append("    <description>3D Geospatial survey with 6-DoF camera poses and embedded telemetry captures.</description>\n")

                sb.append("""
    <Style id="cameraPlacemark">
      <IconStyle>
        <scale>1.2</scale>
        <Icon>
          <href>http://maps.google.com/mapfiles/kml/shapes/camera.png</href>
        </Icon>
      </IconStyle>
      <BalloonStyle>
        <bgColor>ffffffff</bgColor>
        <text><![CDATA[
          $[description]
        ]]></text>
      </BalloonStyle>
    </Style>
""").append("\n")

                val fileEntries = mutableListOf<Pair<String, Uri>>()

                items.forEachIndexed { index, item ->
                    val data = item.telemetry
                    val lat = data.latitude ?: return@forEachIndexed
                    val lon = data.longitude ?: return@forEachIndexed
                    val alt = data.altitudeMeters ?: 10.0
                    val heading = data.effectiveHeading.toDouble()
                    val pitch = data.elevationDegrees.toDouble()
                    val tilt = (90.0 + pitch).coerceIn(0.0, 180.0)
                    val roll = data.rollDegrees.toDouble()

                    val cleanName = item.displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    val safeFileName = "img_${index + 1}_${cleanName}"
                    fileEntries.add(Pair(safeFileName, item.photoUri))

                    sb.append("    <Placemark>\n")
                    sb.append("      <name>${escapeXml(item.displayName)}</name>\n")
                    sb.append("      <styleUrl>#cameraPlacemark</styleUrl>\n")

                    // 3D Virtual Camera Pose
                    sb.append("      <Camera>\n")
                    sb.append("        <longitude>$lon</longitude>\n")
                    sb.append("        <latitude>$lat</latitude>\n")
                    sb.append("        <altitude>$alt</altitude>\n")
                    sb.append("        <heading>$heading</heading>\n")
                    sb.append("        <tilt>$tilt</tilt>\n")
                    sb.append("        <roll>$roll</roll>\n")
                    sb.append("        <altitudeMode>absolute</altitudeMode>\n")
                    sb.append("      </Camera>\n")

                    // Embedded photo info balloon
                    sb.append("      <description><![CDATA[\n")
                    sb.append("        <div style=\"font-family: -apple-system, Roboto, sans-serif; min-width: 280px; max-width: 440px; color: #1a202c;\">\n")
                    sb.append("          <h3 style=\"margin: 0 0 8px 0; color: #00838f;\">${escapeXml(item.displayName)}</h3>\n")
                    sb.append("          <img src=\"files/${safeFileName}\" style=\"width: 100%; border-radius: 6px; box-shadow: 0 2px 6px rgba(0,0,0,0.2); margin-bottom: 8px;\" /><br/>\n")
                    sb.append("          <table style=\"width: 100%; font-size: 11.5px; border-collapse: collapse; line-height: 1.5;\">\n")
                    sb.append("            <tr style=\"background: #e0f7fa;\"><td style=\"padding: 3px 6px; font-weight: bold;\">True Bearing:</td><td style=\"padding: 3px 6px;\">${data.formattedHeading()}</td></tr>\n")
                    sb.append("            <tr><td style=\"padding: 3px 6px; font-weight: bold;\">Pitch / Tilt:</td><td style=\"padding: 3px 6px;\">${data.formattedElevation()}</td></tr>\n")
                    sb.append("            <tr style=\"background: #e0f7fa;\"><td style=\"padding: 3px 6px; font-weight: bold;\">Lateral Roll:</td><td style=\"padding: 3px 6px;\">${data.formattedRoll()}</td></tr>\n")
                    sb.append("            <tr><td style=\"padding: 3px 6px; font-weight: bold;\">Altitude (MSL):</td><td style=\"padding: 3px 6px;\">${data.formattedAltitude(UnitSystem.METRIC)} (${data.formattedAltitude(UnitSystem.IMPERIAL)})</td></tr>\n")
                    if (data.pressureHpa != null) {
                        sb.append("            <tr style=\"background: #e0f7fa;\"><td style=\"padding: 3px 6px; font-weight: bold;\">Barometer:</td><td style=\"padding: 3px 6px;\">${data.formattedPressure(UnitSystem.METRIC)} (${data.formattedPressure(UnitSystem.IMPERIAL)})</td></tr>\n")
                    }
                    if (data.temperatureCelsius != null) {
                        sb.append("            <tr><td style=\"padding: 3px 6px; font-weight: bold;\">Temperature:</td><td style=\"padding: 3px 6px;\">${data.formattedTemperature(UnitSystem.METRIC)} (${data.formattedTemperature(UnitSystem.IMPERIAL)})</td></tr>\n")
                    }
                    if (data.speedMps != null) {
                        sb.append("            <tr style=\"background: #e0f7fa;\"><td style=\"padding: 3px 6px; font-weight: bold;\">Speed:</td><td style=\"padding: 3px 6px;\">${data.formattedSpeed(UnitSystem.IMPERIAL)}</td></tr>\n")
                    }
                    sb.append("            <tr><td style=\"padding: 3px 6px; font-weight: bold;\">Coordinates:</td><td style=\"padding: 3px 6px;\">${data.formattedCoordinatesDecimal()}<br/><span style=\"color:#555;\">${data.formattedCoordinatesDms()}</span></td></tr>\n")
                    sb.append("            <tr style=\"background: #e0f7fa;\"><td style=\"padding: 3px 6px; font-weight: bold;\">Capture Time:</td><td style=\"padding: 3px 6px;\">${data.formattedDateTime()}</td></tr>\n")
                    sb.append("          </table>\n")
                    sb.append("        </div>\n")
                    sb.append("      ]]></description>\n")

                    sb.append("      <Point>\n")
                    sb.append("        <altitudeMode>absolute</altitudeMode>\n")
                    sb.append("        <coordinates>$lon,$lat,$alt</coordinates>\n")
                    sb.append("      </Point>\n")
                    sb.append("    </Placemark>\n")
                }

                sb.append("  </Document>\n")
                sb.append("</kml>")

                val kmlEntry = ZipEntry("doc.kml")
                zos.putNextEntry(kmlEntry)
                zos.write(sb.toString().toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                fileEntries.forEach { (safeName, uri) ->
                    try {
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            val imgEntry = ZipEntry("files/$safeName")
                            zos.putNextEntry(imgEntry)
                            inputStream.copyTo(zos)
                            zos.closeEntry()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            saveBinaryFileToMediaStore(
                context = context,
                fileName = fileName,
                mimeType = "application/vnd.google-earth.kmz",
                sourceFile = tempFile
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    /**
     * Exports a Google Earth / GIS compatible KML file with photo waypoint placemarks and 3D camera poses.
     */
    suspend fun exportKml(
        context: Context,
        items: List<Pair<String, TelemetryData>>
    ): Uri? = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "TELEMETRY_SURVEY_${timestamp}.kml"

        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append("\n")
        sb.append("""<kml xmlns="http://www.opengis.net/kml/2.2">""").append("\n")
        sb.append("  <Document>\n")
        sb.append("    <name>Telemetry Camera Field Survey</name>\n")
        sb.append("    <description>Exported field survey points with 6-DoF spatial telemetry</description>\n")

        for ((name, data) in items) {
            val lat = data.latitude ?: continue
            val lon = data.longitude ?: continue
            val alt = data.altitudeMeters ?: 10.0
            val heading = data.effectiveHeading.toDouble()
            val pitch = data.elevationDegrees.toDouble()
            val tilt = (90.0 + pitch).coerceIn(0.0, 180.0)
            val roll = data.rollDegrees.toDouble()

            sb.append("    <Placemark>\n")
            sb.append("      <name>${escapeXml(name)}</name>\n")

            // 3D Camera Pose
            sb.append("      <Camera>\n")
            sb.append("        <longitude>$lon</longitude>\n")
            sb.append("        <latitude>$lat</latitude>\n")
            sb.append("        <altitude>$alt</altitude>\n")
            sb.append("        <heading>$heading</heading>\n")
            sb.append("        <tilt>$tilt</tilt>\n")
            sb.append("        <roll>$roll</roll>\n")
            sb.append("        <altitudeMode>absolute</altitudeMode>\n")
            sb.append("      </Camera>\n")

            sb.append("      <description><![CDATA[\n")
            sb.append("        <b>Elevation/Pitch:</b> ${data.formattedElevation()}<br/>\n")
            sb.append("        <b>Heading:</b> ${data.formattedHeading()}<br/>\n")
            sb.append("        <b>Roll:</b> ${data.formattedRoll()}<br/>\n")
            sb.append("        <b>Altitude:</b> ${data.formattedAltitude(UnitSystem.IMPERIAL)}<br/>\n")
            if (data.pressureHpa != null) sb.append("        <b>Barometer:</b> ${data.formattedPressure(UnitSystem.IMPERIAL)}<br/>\n")
            if (data.temperatureCelsius != null) sb.append("        <b>Temperature:</b> ${data.formattedTemperature(UnitSystem.IMPERIAL)}<br/>\n")
            if (data.speedMps != null) sb.append("        <b>Speed:</b> ${data.formattedSpeed(UnitSystem.IMPERIAL)}<br/>\n")
            sb.append("        <b>Time:</b> ${data.formattedDateTime()}<br/>\n")
            sb.append("      ]]></description>\n")
            sb.append("      <Point>\n")
            sb.append("        <altitudeMode>absolute</altitudeMode>\n")
            sb.append("        <coordinates>$lon,$lat,$alt</coordinates>\n")
            sb.append("      </Point>\n")
            sb.append("    </Placemark>\n")
        }

        sb.append("  </Document>\n")
        sb.append("</kml>")

        saveTextFileToMediaStore(
            context = context,
            fileName = fileName,
            mimeType = "application/vnd.google-earth.kml+xml",
            content = sb.toString()
        )
    }

    /**
     * Exports a standard GPX (GPS Exchange Format) waypoint file.
     */
    suspend fun exportGpx(
        context: Context,
        items: List<Pair<String, TelemetryData>>
    ): Uri? = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "TELEMETRY_WAYPOINTS_${timestamp}.gpx"

        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append("\n")
        sb.append("""<gpx version="1.1" creator="TelemetryCamera" xmlns="http://www.topografix.com/GPX/1/1">""").append("\n")

        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }

        for ((name, data) in items) {
            val lat = data.latitude ?: continue
            val lon = data.longitude ?: continue

            sb.append("""  <wpt lat="$lat" lon="$lon">""").append("\n")
            data.altitudeMeters?.let { sb.append("    <ele>$it</ele>\n") }
            sb.append("    <time>${isoFormat.format(Date(data.timestamp))}</time>\n")
            sb.append("    <name>${escapeXml(name)}</name>\n")
            sb.append("    <desc>${escapeXml("Hdg: ${data.formattedHeading()}, Pitch: ${data.formattedElevation()}, Roll: ${data.formattedRoll()}")}</desc>\n")
            sb.append("    <sym>Camera</sym>\n")
            sb.append("  </wpt>\n")
        }

        sb.append("</gpx>")

        saveTextFileToMediaStore(
            context = context,
            fileName = fileName,
            mimeType = "application/gpx+xml",
            content = sb.toString()
        )
    }

    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun saveBinaryFileToMediaStore(
        context: Context,
        fileName: String,
        mimeType: String,
        sourceFile: File
    ): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/TelemetryCamera")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        sourceFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                }
                uri
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "TelemetryCamera")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                sourceFile.inputStream().use { input ->
                    FileOutputStream(file).use { out ->
                        input.copyTo(out)
                    }
                }
                Uri.fromFile(file)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun saveTextFileToMediaStore(
        context: Context,
        fileName: String,
        mimeType: String,
        content: String
    ): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/TelemetryCamera")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(content.toByteArray(Charsets.UTF_8))
                    }
                }
                uri
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "TelemetryCamera")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                FileOutputStream(file).use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                }
                Uri.fromFile(file)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
