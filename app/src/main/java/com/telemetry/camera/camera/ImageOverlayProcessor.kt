package com.telemetry.camera.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.telemetry.camera.data.AppSettings
import com.telemetry.camera.data.CompassDisplayMode
import com.telemetry.camera.data.GpsDisplayMode
import com.telemetry.camera.data.TelemetryData
import kotlin.math.max

object ImageOverlayProcessor {

    fun burnOverlayIntoBitmap(
        source: Bitmap,
        telemetry: TelemetryData,
        settings: AppSettings = AppSettings()
    ): Bitmap {
        val mutableBitmap = if (source.isMutable) {
            source
        } else {
            source.copy(Bitmap.Config.ARGB_8888, true)
        }

        val canvas = Canvas(mutableBitmap)
        val width = canvas.width.toFloat()
        val height = canvas.height.toFloat()

        // Base dimension scale factor relative to standard 1080p, multiplied by user display size
        val scale = (max(width, height) / 1920f) * settings.displaySize.scaleFactor
        val padding = 32f * scale
        val cornerRadius = 16f * scale

        // Paint definitions
        val bgPaint = Paint().apply {
            color = Color.argb(160, 15, 23, 42) // Dark translucent slate
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val borderPaint = Paint().apply {
            color = Color.argb(200, 56, 189, 248) // Cyber cyan border
            style = Paint.Style.STROKE
            strokeWidth = 2f * scale
            isAntiAlias = true
        }

        val primaryTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = 28f * scale
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
            setShadowLayer(4f * scale, 0f, 0f, Color.BLACK)
        }

        val accentTextPaint = Paint().apply {
            color = Color.rgb(56, 189, 248) // Cyan accent
            textSize = 28f * scale
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
            setShadowLayer(4f * scale, 0f, 0f, Color.BLACK)
        }

        val amberTextPaint = Paint().apply {
            color = Color.rgb(251, 191, 36) // Amber for Elevation
            textSize = 34f * scale
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
            setShadowLayer(5f * scale, 0f, 0f, Color.BLACK)
        }

        val hudLinePaint = Paint().apply {
            color = Color.argb(220, 251, 191, 36)
            style = Paint.Style.STROKE
            strokeWidth = 3f * scale
            isAntiAlias = true
            setShadowLayer(3f * scale, 0f, 0f, Color.BLACK)
        }

        val lineSpacing = 36f * scale

        // ==========================================
        // 1. TOP TELEMETRY CARD (Date/Time, Alt, GPS, DMS)
        // ==========================================
        val timeLines = if (settings.showTime) 1 else 0
        val altLines = if (settings.showAltitude) 1 else 0
        val baroLines = if (settings.showPressure && telemetry.pressureHpa != null) 1 else 0
        val tempLines = if (settings.showTemperature && telemetry.temperatureCelsius != null) 1 else 0
        val gpsLines = when (settings.gpsDisplayMode) {
            GpsDisplayMode.BOTH -> 2
            GpsDisplayMode.DECIMAL, GpsDisplayMode.DMS -> 1
            GpsDisplayMode.NONE -> 0
        }
        val totalLines = timeLines + altLines + baroLines + tempLines + gpsLines

        if (totalLines > 0) {
            val topCardHeight = (30f + totalLines * 36f) * scale
            val topRect = RectF(padding, padding, width - padding, padding + topCardHeight)
            canvas.drawRoundRect(topRect, cornerRadius, cornerRadius, bgPaint)
            canvas.drawRoundRect(topRect, cornerRadius, cornerRadius, borderPaint)

            var textY = padding + 40f * scale
            var hasDrawnLine = false

            // Row 1: Timestamp
            if (settings.showTime) {
                canvas.drawText("DATE/TIME: ", padding + 24f * scale, textY, accentTextPaint)
                canvas.drawText(telemetry.formattedDateTime(), padding + 220f * scale, textY, primaryTextPaint)
                hasDrawnLine = true
            }

            // Row 2: Altitude & Accuracy
            if (settings.showAltitude) {
                if (hasDrawnLine) textY += lineSpacing
                val altText = telemetry.formattedAltitude(settings.unitSystem)
                val accText = telemetry.formattedAccuracy(settings.unitSystem)
                canvas.drawText("ALTITUDE:  ", padding + 24f * scale, textY, accentTextPaint)
                canvas.drawText("$altText$accText", padding + 220f * scale, textY, primaryTextPaint)
                hasDrawnLine = true
            }

            // Row 3: Barometer
            if (settings.showPressure && telemetry.pressureHpa != null) {
                if (hasDrawnLine) textY += lineSpacing
                canvas.drawText("BAROMETER: ", padding + 24f * scale, textY, accentTextPaint)
                canvas.drawText(telemetry.formattedPressure(settings.unitSystem), padding + 220f * scale, textY, primaryTextPaint)
                hasDrawnLine = true
            }

            // Row 4: Temperature
            if (settings.showTemperature && telemetry.temperatureCelsius != null) {
                if (hasDrawnLine) textY += lineSpacing
                canvas.drawText("TEMP:      ", padding + 24f * scale, textY, accentTextPaint)
                canvas.drawText(telemetry.formattedTemperature(settings.unitSystem), padding + 220f * scale, textY, primaryTextPaint)
                hasDrawnLine = true
            }

            // Row 5: GPS Decimal
            if (settings.gpsDisplayMode == GpsDisplayMode.BOTH || settings.gpsDisplayMode == GpsDisplayMode.DECIMAL) {
                if (hasDrawnLine) textY += lineSpacing
                canvas.drawText("GPS DEG:   ", padding + 24f * scale, textY, accentTextPaint)
                canvas.drawText(telemetry.formattedCoordinatesDecimal(), padding + 220f * scale, textY, primaryTextPaint)
                hasDrawnLine = true
            }

            // Row 6: GPS DMS
            if (settings.gpsDisplayMode == GpsDisplayMode.BOTH || settings.gpsDisplayMode == GpsDisplayMode.DMS) {
                if (hasDrawnLine) textY += lineSpacing
                canvas.drawText("GPS DMS:   ", padding + 24f * scale, textY, accentTextPaint)
                canvas.drawText(telemetry.formattedCoordinatesDms(), padding + 220f * scale, textY, primaryTextPaint)
                hasDrawnLine = true
            }
        }

        // ==========================================
        // 2. CENTER RETICLE & PITCH LADDER
        // ==========================================
        val centerX = width / 2f
        val centerY = height / 2f
        val crosshairRadius = 40f * scale

        if (settings.showHorizonReticle) {
            // Center crosshair dot and reticle ring
            canvas.drawCircle(centerX, centerY, crosshairRadius, borderPaint)
            canvas.drawCircle(centerX, centerY, 4f * scale, amberTextPaint)

            // Crosshair ticks
            canvas.drawLine(centerX - crosshairRadius - 20f * scale, centerY, centerX - crosshairRadius, centerY, hudLinePaint)
            canvas.drawLine(centerX + crosshairRadius, centerY, centerX + crosshairRadius + 20f * scale, centerY, hudLinePaint)
            canvas.drawLine(centerX, centerY - crosshairRadius - 20f * scale, centerX, centerY - crosshairRadius, hudLinePaint)
            canvas.drawLine(centerX, centerY + crosshairRadius, centerX, centerY + crosshairRadius + 20f * scale, hudLinePaint)

            if (settings.showAngularRangingGrid) {
                val tickPaint = Paint().apply {
                    color = settings.rangingGridColor.toInt()
                    style = Paint.Style.STROKE
                    strokeWidth = settings.rangingGridThickness * scale
                    strokeCap = Paint.Cap.ROUND
                    isAntiAlias = true
                }
                for (step in 1..3) {
                    val offset = (step * 30f) * scale
                    val tickLen = (if (step % 2 == 0) 12f else 6f) * scale
                    canvas.drawLine(centerX - crosshairRadius - offset, centerY - tickLen, centerX - crosshairRadius - offset, centerY + tickLen, tickPaint)
                    canvas.drawLine(centerX + crosshairRadius + offset, centerY - tickLen, centerX + crosshairRadius + offset, centerY + tickLen, tickPaint)
                    canvas.drawLine(centerX - tickLen, centerY - crosshairRadius - offset, centerX + tickLen, centerY - crosshairRadius - offset, tickPaint)
                    canvas.drawLine(centerX - tickLen, centerY + crosshairRadius + offset, centerX + tickLen, centerY + crosshairRadius + offset, tickPaint)
                }
            }

            if (settings.showSunMoonEphemeris && telemetry.sunAzimuth != null && telemetry.sunElevation != null) {
                val ephText = "☀️ SUN: ${telemetry.sunAzimuth.toInt()}° Az, ${if (telemetry.sunElevation >= 0) "+" else ""}${telemetry.sunElevation.toInt()}° El"
                val ephWidth = amberTextPaint.measureText(ephText)
                canvas.drawText(ephText, centerX - ephWidth / 2f, centerY - crosshairRadius - 30f * scale, amberTextPaint)
            }
        }

        // ==========================================
        // 3. BOTTOM TELEMETRY CARDS (Cockpit & Motion)
        // ==========================================
        val isLandscape = width > height
        val showBottomCockpit = settings.compassDisplayMode != CompassDisplayMode.NONE || settings.showElevation || settings.showRoll
        val showMotionCard = settings.showSpeed || settings.showTrackBearing
        val bottomCardHeight = 84f * scale
        val cardGap = 16f * scale

        if (showBottomCockpit || showMotionCard) {
            val cockpitRect: RectF?
            val motionRect: RectF?

            if (!isLandscape) {
                // PORTRAIT: Motion card stacked vertically ABOVE Cockpit card
                if (showBottomCockpit && showMotionCard) {
                    cockpitRect = RectF(padding, height - padding - bottomCardHeight, width - padding, height - padding)
                    val motionBottom = cockpitRect.top - cardGap
                    motionRect = RectF(padding, motionBottom - bottomCardHeight, width - padding, motionBottom)
                } else if (showBottomCockpit) {
                    cockpitRect = RectF(padding, height - padding - bottomCardHeight, width - padding, height - padding)
                    motionRect = null
                } else {
                    cockpitRect = null
                    motionRect = RectF(padding, height - padding - bottomCardHeight, width - padding, height - padding)
                }
            } else {
                // LANDSCAPE: Cockpit card on the left, Motion card to the RIGHT
                if (showBottomCockpit && showMotionCard) {
                    val availableWidth = (width - 2 * padding - cardGap)
                    val leftWidth = availableWidth * 0.52f
                    cockpitRect = RectF(padding, height - padding - bottomCardHeight, padding + leftWidth, height - padding)
                    motionRect = RectF(padding + leftWidth + cardGap, height - padding - bottomCardHeight, width - padding, height - padding)
                } else if (showBottomCockpit) {
                    val cardWidth = (width - 2 * padding) * 0.55f
                    cockpitRect = RectF(padding, height - padding - bottomCardHeight, padding + cardWidth, height - padding)
                    motionRect = null
                } else {
                    val cardWidth = (width - 2 * padding) * 0.55f
                    cockpitRect = null
                    motionRect = RectF(padding, height - padding - bottomCardHeight, padding + cardWidth, height - padding)
                }
            }

            // Draw Cockpit Card (Heading, Roll, Elevation)
            if (cockpitRect != null) {
                canvas.drawRoundRect(cockpitRect, cornerRadius, cornerRadius, bgPaint)
                canvas.drawRoundRect(cockpitRect, cornerRadius, cornerRadius, borderPaint)
                val cockpitTextY = cockpitRect.centerY() + 11f * scale

                if (settings.compassDisplayMode != CompassDisplayMode.NONE) {
                    canvas.drawText("HEADING: ", cockpitRect.left + 24f * scale, cockpitTextY, accentTextPaint)
                    canvas.drawText(
                        telemetry.formattedHeadingWithMode(settings.compassDisplayMode),
                        cockpitRect.left + 170f * scale,
                        cockpitTextY,
                        primaryTextPaint
                    )
                }

                val rollOffset = if (settings.showRoll) {
                    val rollText = "ROLL: ${telemetry.formattedRoll()}"
                    val rollTextWidth = primaryTextPaint.measureText(rollText)
                    canvas.drawText(rollText, cockpitRect.right - 24f * scale - rollTextWidth, cockpitTextY, primaryTextPaint)
                    rollTextWidth + 24f * scale
                } else {
                    0f
                }

                if (settings.showElevation) {
                    val elevText = "ELEV: ${telemetry.formattedElevation()}"
                    val elevTextWidth = amberTextPaint.measureText(elevText)
                    val elevRightPos = cockpitRect.right - 24f * scale - rollOffset - elevTextWidth
                    canvas.drawText(elevText, elevRightPos, cockpitTextY, amberTextPaint)
                }
            }

            // Draw Motion Card (GPS Speed, Track Bearing)
            if (motionRect != null) {
                canvas.drawRoundRect(motionRect, cornerRadius, cornerRadius, bgPaint)
                canvas.drawRoundRect(motionRect, cornerRadius, cornerRadius, borderPaint)
                val motionTextY = motionRect.centerY() + 11f * scale

                if (settings.showSpeed && settings.showTrackBearing) {
                    // Speed on Left
                    canvas.drawText("SPEED: ", motionRect.left + 24f * scale, motionTextY, accentTextPaint)
                    canvas.drawText(telemetry.formattedSpeed(settings.unitSystem), motionRect.left + 140f * scale, motionTextY, primaryTextPaint)

                    // Track on Right
                    val trkVal = telemetry.formattedBearing()
                    val trkValWidth = primaryTextPaint.measureText(trkVal)
                    canvas.drawText(trkVal, motionRect.right - 24f * scale - trkValWidth, motionTextY, primaryTextPaint)

                    val trkLabel = "TRACK: "
                    val trkLabelWidth = accentTextPaint.measureText(trkLabel)
                    canvas.drawText(trkLabel, motionRect.right - 24f * scale - trkValWidth - trkLabelWidth, motionTextY, accentTextPaint)
                } else if (settings.showSpeed) {
                    val spdText = "SPEED: ${telemetry.formattedSpeed(settings.unitSystem)}"
                    val spdWidth = primaryTextPaint.measureText(spdText)
                    canvas.drawText(spdText, motionRect.centerX() - spdWidth / 2f, motionTextY, primaryTextPaint)
                } else if (settings.showTrackBearing) {
                    val trkText = "TRACK: ${telemetry.formattedBearing()}"
                    val trkWidth = primaryTextPaint.measureText(trkText)
                    canvas.drawText(trkText, motionRect.centerX() - trkWidth / 2f, motionTextY, primaryTextPaint)
                }
            }
        }

        // ==========================================
        // 4. OPTIONAL APP WATERMARK BADGE
        // ==========================================
        if (settings.showWatermarkBadge) {
            if (totalLines > 0) {
                drawWatermarkBadge(
                    canvas = canvas,
                    x = width - padding - 14f * scale,
                    y = padding + 12f * scale,
                    scale = scale,
                    alignRight = true,
                    alignBottom = false
                )
            } else {
                drawWatermarkBadge(
                    canvas = canvas,
                    x = width - padding,
                    y = height - padding,
                    scale = scale,
                    alignRight = true,
                    alignBottom = true
                )
            }
        }

        return mutableBitmap
    }

    fun burnWatermarkBadgeOnly(
        source: Bitmap,
        settings: AppSettings = AppSettings()
    ): Bitmap {
        if (!settings.showWatermarkBadge) return source

        val mutableBitmap = if (source.isMutable) {
            source
        } else {
            source.copy(Bitmap.Config.ARGB_8888, true)
        }

        val canvas = Canvas(mutableBitmap)
        val width = canvas.width.toFloat()
        val height = canvas.height.toFloat()
        val scale = (kotlin.math.min(width, height) / 1080f).coerceAtLeast(1.0f) * settings.displaySize.scaleFactor
        val padding = 32f * scale

        drawWatermarkBadge(
            canvas = canvas,
            x = width - padding,
            y = height - padding,
            scale = scale,
            alignRight = true,
            alignBottom = true
        )

        return mutableBitmap
    }

    private fun drawWatermarkBadge(
        canvas: Canvas,
        x: Float,
        y: Float,
        scale: Float,
        alignRight: Boolean,
        alignBottom: Boolean
    ) {
        val badgeText = "⌖ TELEMETRY"
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00E5FF")
            textSize = 14f * scale
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val textWidth = badgeTextPaint.measureText(badgeText)
        val hPad = 14f * scale
        val vPad = 8f * scale
        val badgeWidth = textWidth + (hPad * 2)
        val badgeHeight = (badgeTextPaint.textSize * 1.25f) + (vPad * 2)

        val left = if (alignRight) x - badgeWidth else x
        val top = if (alignBottom) y - badgeHeight else y
        val rect = RectF(left, top, left + badgeWidth, top + badgeHeight)
        val cornerRadius = 8f * scale

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D908121E")
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8000E5FF")
            strokeWidth = 1.5f * scale
            style = Paint.Style.STROKE
        }

        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)

        val fontMetrics = badgeTextPaint.fontMetrics
        val textY = rect.centerY() - (fontMetrics.descent + fontMetrics.ascent) / 2f
        canvas.drawText(badgeText, rect.left + hPad, textY, badgeTextPaint)
    }
}
