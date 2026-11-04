package com.telemetry.camera.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.telemetry.camera.data.AppSettings
import com.telemetry.camera.data.CompassDisplayMode
import com.telemetry.camera.data.GpsDisplayMode
import com.telemetry.camera.data.TelemetryData
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

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
        // 2. CENTER ARTIFICIAL HORIZON & RETICLE
        // ==========================================
        val centerX = width / 2f
        val centerY = height / 2f

        if (settings.showHorizonReticle) {
            val pitch = telemetry.elevationDegrees
            val roll = telemetry.rollDegrees
            val isLevel = abs(pitch) < 0.8f && abs(roll) < 0.8f
            val hudCyan = Color.rgb(6, 182, 212)
            val hudBorderCyan = Color.argb(128, 6, 182, 212)
            val hudAmber = Color.rgb(251, 191, 36)
            val hudGreen = Color.rgb(34, 197, 94)
            val bezelColor = Color.argb(115, 51, 65, 85)
            val boresightColor = if (isLevel) hudGreen else hudAmber
            val horizonLineColor = if (isLevel) hudGreen else hudCyan

            val dialRadius = 140f * scale
            val cx = centerX
            val cy = centerY

            // 1. Outer circular bezel
            val bezelPaint = Paint().apply {
                color = bezelColor
                style = Paint.Style.STROKE
                strokeWidth = 2f * scale
                isAntiAlias = true
            }
            canvas.drawCircle(cx, cy, dialRadius, bezelPaint)

            // Top bank angle arc from -60° to +60°
            val arcPaint = Paint().apply {
                color = hudBorderCyan
                style = Paint.Style.STROKE
                strokeWidth = 2.5f * scale
                isAntiAlias = true
            }
            val arcRect = RectF(cx - dialRadius, cy - dialRadius, cx + dialRadius, cy + dialRadius)
            canvas.drawArc(arcRect, 210f, 120f, false, arcPaint)

            // Bank angle tick marks at 0°, ±10°, ±20°, ±30°, ±45°, ±60°
            val tickPaint = Paint().apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                isAntiAlias = true
            }
            val bankAngles = listOf(-60, -45, -30, -20, -10, 0, 10, 20, 30, 45, 60)
            for (angle in bankAngles) {
                val rad = Math.toRadians((270 + angle).toDouble()).toFloat()
                val isMajor = angle == 0 || abs(angle) == 30 || abs(angle) == 60
                val tickLen = (if (isMajor) 12f else 7f) * scale
                val tickStartX = cx + (dialRadius - tickLen) * cos(rad)
                val tickStartY = cy + (dialRadius - tickLen) * sin(rad)
                val tickEndX = cx + dialRadius * cos(rad)
                val tickEndY = cy + dialRadius * sin(rad)
                tickPaint.color = if (angle == 0) hudAmber else hudCyan
                tickPaint.strokeWidth = (if (isMajor) 2f else 1.2f) * scale
                canvas.drawLine(tickStartX, tickStartY, tickEndX, tickEndY, tickPaint)
            }

            // Top dead center 0° zero-roll index mark (inverted triangle)
            val indexTopY = cy - dialRadius - 4f * scale
            val indexSize = 7f * scale
            val zeroIndexTriangle = Path().apply {
                moveTo(cx, indexTopY + indexSize)
                lineTo(cx - indexSize, indexTopY)
                lineTo(cx + indexSize, indexTopY)
                close()
            }
            val indexPaint = Paint().apply {
                color = if (isLevel) hudGreen else hudAmber
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            canvas.drawPath(zeroIndexTriangle, indexPaint)

            // Dynamic Roll Pointer (traverses bank arc with -roll)
            val rollRad = Math.toRadians((270 - roll).toDouble()).toFloat()
            val rollPtrDist = dialRadius - 4f * scale
            val ptrTipX = cx + rollPtrDist * cos(rollRad)
            val ptrTipY = cy + rollPtrDist * sin(rollRad)
            val ptrBaseRad = 6f * scale
            val perpRad = rollRad + (Math.PI / 2).toFloat()
            val ptrBaseDist = rollPtrDist - 12f * scale
            val ptrBaseCenterX = cx + ptrBaseDist * cos(rollRad)
            val ptrBaseCenterY = cy + ptrBaseDist * sin(rollRad)
            val rollPointerPath = Path().apply {
                moveTo(ptrTipX, ptrTipY)
                lineTo(ptrBaseCenterX + ptrBaseRad * cos(perpRad), ptrBaseCenterY + ptrBaseRad * sin(perpRad))
                lineTo(ptrBaseCenterX - ptrBaseRad * cos(perpRad), ptrBaseCenterY - ptrBaseRad * sin(perpRad))
                close()
            }
            val rollPtrPaint = Paint().apply {
                color = if (isLevel) hudGreen else hudCyan
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            canvas.drawPath(rollPointerPath, rollPtrPaint)

            // 2. DYNAMIC ARTIFICIAL HORIZON & PITCH LADDER (Clipped inside dial)
            val clipDialPath = Path().apply {
                addOval(RectF(cx - dialRadius + 3f * scale, cy - dialRadius + 3f * scale, cx + dialRadius - 3f * scale, cy + dialRadius - 3f * scale), Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(clipDialPath)
            canvas.rotate(-roll, cx, cy)

            val pitchPixelsPerDegree = 4.0f * scale
            val horizonY = cy + (pitch * pitchPixelsPerDegree)

            // Dynamic 0° Horizon Line
            val horizonWingLen = dialRadius * 0.88f
            val horizonGap = 26f * scale
            val horizonPaint = Paint().apply {
                color = horizonLineColor
                style = Paint.Style.STROKE
                strokeWidth = 3f * scale
                strokeCap = Paint.Cap.ROUND
                isAntiAlias = true
            }
            canvas.drawLine(cx - horizonWingLen, horizonY, cx - horizonGap, horizonY, horizonPaint)
            canvas.drawLine(cx + horizonGap, horizonY, cx + horizonWingLen, horizonY, horizonPaint)

            // Pitch Ladder Rungs (+10°, +20°, -10°, -20°, ±5°, ±15°, ±25°)
            val rungs = listOf(5, 10, 15, 20, 25, 30, -5, -10, -15, -20, -25, -30)
            val rungPaint = Paint().apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                isAntiAlias = true
            }
            for (deg in rungs) {
                val rungY = horizonY - (deg * pitchPixelsPerDegree)
                if (abs(rungY - cy) > dialRadius) continue

                val isMajorRung = deg % 10 == 0
                val rungWidth = (if (isMajorRung) 38f else 20f) * scale
                val rungGap = (if (isMajorRung) 30f else 26f) * scale
                val rungStroke = (if (isMajorRung) 2.6f else 2.0f) * scale
                rungPaint.strokeWidth = rungStroke
                rungPaint.color = if (deg > 0) hudCyan else hudBorderCyan

                // Left horizontal bar
                canvas.drawLine(cx - rungGap - rungWidth, rungY, cx - rungGap, rungY, rungPaint)
                // Right horizontal bar
                canvas.drawLine(cx + rungGap, rungY, cx + rungGap + rungWidth, rungY, rungPaint)

                // Major 10° rungs tips pointing towards horizon
                if (isMajorRung) {
                    val tipLen = 7f * scale
                    val tipDirY = if (deg > 0) tipLen else -tipLen
                    canvas.drawLine(cx - rungGap - rungWidth, rungY, cx - rungGap - rungWidth, rungY + tipDirY, rungPaint)
                    canvas.drawLine(cx + rungGap + rungWidth, rungY, cx + rungGap + rungWidth, rungY + tipDirY, rungPaint)
                }
            }
            canvas.restore()

            // 3. FIXED AIRCRAFT WATERLINE / BORESIGHT (Dead Center)
            val centerDotRadius = 4.5f * scale
            val ringRadius = 16f * scale
            val wingGap = 23f * scale
            val wingSpan = 44f * scale
            val wingDrop = 7f * scale
            val waterlinePaint = Paint().apply {
                color = boresightColor
                style = Paint.Style.STROKE
                strokeWidth = 3f * scale
                strokeCap = Paint.Cap.ROUND
                isAntiAlias = true
            }

            val dotPaint = Paint().apply {
                color = boresightColor
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            canvas.drawCircle(cx, cy, centerDotRadius, dotPaint)
            canvas.drawCircle(cx, cy, ringRadius, waterlinePaint)

            val leftWingPath = Path().apply {
                moveTo(cx - wingGap - wingSpan, cy)
                lineTo(cx - wingGap, cy)
                lineTo(cx - wingGap, cy + wingDrop)
            }
            canvas.drawPath(leftWingPath, waterlinePaint)

            val rightWingPath = Path().apply {
                moveTo(cx + wingGap + wingSpan, cy)
                lineTo(cx + wingGap, cy)
                lineTo(cx + wingGap, cy + wingDrop)
            }
            canvas.drawPath(rightWingPath, waterlinePaint)

            // Optional Angular Ranging Grid ticks
            if (settings.showAngularRangingGrid) {
                val gridPaint = Paint().apply {
                    color = settings.rangingGridColor.toInt()
                    style = Paint.Style.STROKE
                    strokeWidth = settings.rangingGridThickness * scale
                    strokeCap = Paint.Cap.ROUND
                    isAntiAlias = true
                }
                for (step in 1..3) {
                    val offset = (step * 38f) * scale
                    val tickLen = (if (step % 2 == 0) 14f else 8f) * scale
                    canvas.drawLine(cx - dialRadius - offset, cy - tickLen, cx - dialRadius - offset, cy + tickLen, gridPaint)
                    canvas.drawLine(cx + dialRadius + offset, cy - tickLen, cx + dialRadius + offset, cy + tickLen, gridPaint)
                    canvas.drawLine(cx - tickLen, cy - dialRadius - offset, cx + tickLen, cy - dialRadius - offset, gridPaint)
                    canvas.drawLine(cx - tickLen, cy + dialRadius + offset, cx + tickLen, cy + dialRadius + offset, gridPaint)
                }
            }

            if (settings.showSunMoonEphemeris && telemetry.sunAzimuth != null && telemetry.sunElevation != null) {
                val ephText = "☀️ SUN: ${telemetry.sunAzimuth.toInt()}° Az, ${if (telemetry.sunElevation >= 0) "+" else ""}${telemetry.sunElevation.toInt()}° El"
                val ephWidth = amberTextPaint.measureText(ephText)
                canvas.drawText(ephText, cx - ephWidth / 2f, cy - dialRadius - 18f * scale, amberTextPaint)
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
        // 4. APP WATERMARK BADGE
        // ==========================================
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
