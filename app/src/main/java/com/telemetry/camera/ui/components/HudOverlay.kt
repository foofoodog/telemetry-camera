package com.telemetry.camera.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.*
import com.telemetry.camera.data.AppSettings
import com.telemetry.camera.data.CompassDisplayMode
import com.telemetry.camera.data.GpsDisplayMode
import com.telemetry.camera.data.TelemetryData
import com.telemetry.camera.data.UnitSystem
import com.telemetry.camera.ui.theme.*

@Composable
fun HudOverlay(
    telemetry: TelemetryData,
    settings: AppSettings = AppSettings(),
    isLandscape: Boolean = false,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    topCardOffset: Dp = 0.dp
) {
    val scale = settings.displaySize.scaleFactor

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(
                    top = if (isLandscape) (6 * scale).dp else (18 * scale).dp,
                    start = (14 * scale).dp,
                    end = if (isLandscape) (96 * scale).dp else (14 * scale).dp,
                    bottom = if (isLandscape) (10 * scale).dp else (10 * scale).dp
                )
        ) {
            // 1. TOP TELEMETRY CARD (Each metric on its own dedicated line)
            val hasTopItems = settings.showTime ||
                settings.showAltitude ||
                (settings.showPressure && telemetry.pressureHpa != null) ||
                (settings.showTemperature && telemetry.temperatureCelsius != null) ||
                (settings.gpsDisplayMode != GpsDisplayMode.NONE && telemetry.latitude != null)

            if (hasTopItems) {
                TopTelemetryCard(
                    telemetry = telemetry,
                    settings = settings,
                    scale = scale,
                    modifier = Modifier
                        .align(if (isLandscape) Alignment.TopStart else Alignment.TopCenter)
                        .padding(top = topCardOffset)
                        .then(
                            if (isLandscape) Modifier.widthIn(max = (460 * scale).dp)
                            else Modifier.fillMaxWidth()
                        )
                )
            }

            // 2. CENTER RETICLE / ARTIFICIAL HORIZON INSTRUMENT
            if (settings.showHorizonReticle) {
                CenterReticle(
                    roll = telemetry.rollDegrees,
                    pitch = telemetry.elevationDegrees,
                    scale = scale,
                    showAngularRangingGrid = settings.showAngularRangingGrid,
                    gridColor = Color(settings.rangingGridColor),
                    gridThickness = settings.rangingGridThickness,
                    showEphemeris = settings.showSunMoonEphemeris,
                    telemetry = telemetry,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size((280 * scale).dp)
                )
            }

            // 3. BOTTOM DASHBOARD (Motion & Cockpit Cards)
            val showBottomCockpit = settings.compassDisplayMode != CompassDisplayMode.NONE || settings.showElevation || settings.showRoll
            val showMotionCard = settings.showSpeed || settings.showTrackBearing

            if (showBottomCockpit || showMotionCard) {
                if (!isLandscape) {
                    // PORTRAIT: Stacked vertically at bottom center (Speed/Track card ABOVE Cockpit card)
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(bottom = 158.dp), // Clear fixed camera shutter & mode selector buttons in portrait
                        verticalArrangement = Arrangement.spacedBy((6 * scale).dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (showMotionCard) {
                            MotionTelemetryCard(
                                telemetry = telemetry,
                                settings = settings,
                                scale = scale,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (showBottomCockpit) {
                            BottomTelemetryCard(
                                telemetry = telemetry,
                                settings = settings,
                                scale = scale,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else {
                    // LANDSCAPE: Placed side-by-side along bottom start (Speed/Track card to the RIGHT of Cockpit card)
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .wrapContentWidth()
                            .padding(bottom = 0.dp),
                        horizontalArrangement = Arrangement.spacedBy((8 * scale).dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        if (showBottomCockpit) {
                            BottomTelemetryCard(
                                telemetry = telemetry,
                                settings = settings,
                                scale = scale,
                                modifier = Modifier.widthIn(max = (440 * scale).dp)
                            )
                        }
                        if (showMotionCard) {
                            MotionTelemetryCard(
                                telemetry = telemetry,
                                settings = settings,
                                scale = scale,
                                modifier = Modifier.widthIn(max = (360 * scale).dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopTelemetryCard(
    telemetry: TelemetryData,
    settings: AppSettings,
    scale: Float,
    modifier: Modifier = Modifier
) {
    val labelSize = (10 * scale).sp
    val valueSize = (11.5f * scale).sp
    val rowSpacing = (4 * scale).dp
    val gpsMode = settings.gpsDisplayMode

    Column(
        modifier = modifier
            .background(HudTranslucentDark, RoundedCornerShape((10 * scale).dp))
            .border(1.dp, HudBorderCyan, RoundedCornerShape((10 * scale).dp))
            .padding(horizontal = (12 * scale).dp, vertical = (8 * scale).dp)
    ) {
        var hasDrawnLine = false

        // Line 1: Date & Time
        if (settings.showTime) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TIME: ",
                    color = HudCyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = labelSize
                )
                Text(
                    text = telemetry.formattedDateTime(),
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = valueSize,
                    maxLines = 1,
                    softWrap = false
                )
            }
            hasDrawnLine = true
        }

        // Line 2: Altitude & Accuracy
        if (settings.showAltitude) {
            if (hasDrawnLine) Spacer(modifier = Modifier.height(rowSpacing))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ALT:  ",
                    color = HudCyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = labelSize
                )
                val altText = telemetry.formattedAltitude(settings.unitSystem)
                val accText = telemetry.formattedAccuracy(settings.unitSystem)
                Text(
                    text = "$altText$accText",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = valueSize,
                    maxLines = 1,
                    softWrap = false
                )
            }
            hasDrawnLine = true
        }

        // Line 3: Barometric Pressure (if enabled)
        if (settings.showPressure && telemetry.pressureHpa != null) {
            if (hasDrawnLine) Spacer(modifier = Modifier.height(rowSpacing))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BARO: ",
                    color = HudCyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = labelSize
                )
                Text(
                    text = telemetry.formattedPressure(settings.unitSystem),
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = valueSize,
                    maxLines = 1,
                    softWrap = false
                )
            }
            hasDrawnLine = true
        }

        // Line 4: Temperature (if enabled)
        if (settings.showTemperature && telemetry.temperatureCelsius != null) {
            if (hasDrawnLine) Spacer(modifier = Modifier.height(rowSpacing))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TEMP: ",
                    color = HudCyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = labelSize
                )
                Text(
                    text = telemetry.formattedTemperature(settings.unitSystem),
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = valueSize,
                    maxLines = 1,
                    softWrap = false
                )
            }
            hasDrawnLine = true
        }

        // Line 5: GPS Decimal (Dedicated line)
        if (gpsMode == GpsDisplayMode.BOTH || gpsMode == GpsDisplayMode.DECIMAL) {
            if (hasDrawnLine) Spacer(modifier = Modifier.height(rowSpacing))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "GPS:  ",
                    color = HudCyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = labelSize
                )
                Text(
                    text = telemetry.formattedCoordinatesDecimal(),
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = valueSize,
                    maxLines = 1,
                    softWrap = false
                )
            }
            hasDrawnLine = true
        }

        // Line 6: GPS DMS (Dedicated line)
        if (gpsMode == GpsDisplayMode.BOTH || gpsMode == GpsDisplayMode.DMS) {
            if (hasDrawnLine) Spacer(modifier = Modifier.height(rowSpacing))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DMS:  ",
                    color = HudCyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = labelSize
                )
                Text(
                    text = telemetry.formattedCoordinatesDms(),
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = valueSize,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun CenterReticle(
    roll: Float,
    pitch: Float,
    scale: Float,
    showAngularRangingGrid: Boolean = false,
    gridColor: Color = HudAmber,
    gridThickness: Float = 2.0f,
    showEphemeris: Boolean = false,
    telemetry: TelemetryData? = null,
    modifier: Modifier = Modifier
) {
    val isLevel = abs(pitch) < 0.8f && abs(roll) < 0.8f
    val boresightColor = if (isLevel) HudGreen else HudAmber
    val horizonLineColor = if (isLevel) HudGreen else HudCyan

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val dialRadius = (105f * scale).dp.toPx()

            // 1. OUTER ATTITUDE DIAL & BANK ANGLE ARC (Fixed to camera frame)
            // Circular collimator ring (subtle HUD dial boundary)
            drawCircle(
                color = Slate700.copy(alpha = 0.45f),
                radius = dialRadius,
                style = Stroke(width = (1f * scale).dp.toPx())
            )

            // Top bank angle arc from -60° to +60° (where 270° is top dead center)
            drawArc(
                color = HudBorderCyan.copy(alpha = 0.5f),
                startAngle = 210f,
                sweepAngle = 120f,
                useCenter = false,
                topLeft = Offset(cx - dialRadius, cy - dialRadius),
                size = Size(dialRadius * 2f, dialRadius * 2f),
                style = Stroke(width = (1.5f * scale).dp.toPx())
            )

            // Bank angle tick marks at 0°, ±10°, ±20°, ±30°, ±45°, ±60°
            val bankAngles = listOf(-60, -45, -30, -20, -10, 0, 10, 20, 30, 45, 60)
            for (angle in bankAngles) {
                val rad = Math.toRadians((270 + angle).toDouble()).toFloat()
                val isMajor = angle == 0 || abs(angle) == 30 || abs(angle) == 60
                val tickLen = (if (isMajor) 9f * scale else 5f * scale).dp.toPx()
                val tickStartX = cx + (dialRadius - tickLen) * cos(rad)
                val tickStartY = cy + (dialRadius - tickLen) * sin(rad)
                val tickEndX = cx + dialRadius * cos(rad)
                val tickEndY = cy + dialRadius * sin(rad)
                drawLine(
                    color = if (angle == 0) HudAmber else HudCyan.copy(alpha = 0.6f),
                    start = Offset(tickStartX, tickStartY),
                    end = Offset(tickEndX, tickEndY),
                    strokeWidth = (if (isMajor) 1.5f * scale else 1f * scale).dp.toPx()
                )
            }

            // Top dead center 0° zero-roll index mark (inverted triangle)
            val indexTopY = cy - dialRadius - (3f * scale).dp.toPx()
            val indexSize = (5f * scale).dp.toPx()
            val zeroIndexTriangle = Path().apply {
                moveTo(cx, indexTopY + indexSize)
                lineTo(cx - indexSize, indexTopY)
                lineTo(cx + indexSize, indexTopY)
                close()
            }
            drawPath(zeroIndexTriangle, color = if (isLevel) HudGreen else HudAmber)

            // Dynamic Roll Pointer (traverses bank arc with -roll)
            val rollRad = Math.toRadians((270 - roll).toDouble()).toFloat()
            val rollPtrDist = dialRadius - (3f * scale).dp.toPx()
            val ptrTipX = cx + rollPtrDist * cos(rollRad)
            val ptrTipY = cy + rollPtrDist * sin(rollRad)
            val ptrBaseRad = (4.5f * scale).dp.toPx()
            val perpRad = rollRad + (Math.PI / 2).toFloat()
            val ptrBaseDist = rollPtrDist - (9f * scale).dp.toPx()
            val ptrBaseCenterX = cx + ptrBaseDist * cos(rollRad)
            val ptrBaseCenterY = cy + ptrBaseDist * sin(rollRad)
            val rollPointerPath = Path().apply {
                moveTo(ptrTipX, ptrTipY)
                lineTo(ptrBaseCenterX + ptrBaseRad * cos(perpRad), ptrBaseCenterY + ptrBaseRad * sin(perpRad))
                lineTo(ptrBaseCenterX - ptrBaseRad * cos(perpRad), ptrBaseCenterY - ptrBaseRad * sin(perpRad))
                close()
            }
            drawPath(rollPointerPath, color = if (isLevel) HudGreen else HudCyan)

            // 2. DYNAMIC ARTIFICIAL HORIZON & PITCH LADDER (Clipped inside the dial)
            val clipDialPath = Path().apply {
                addOval(Rect(cx - dialRadius + 2f, cy - dialRadius + 2f, cx + dialRadius - 2f, cy + dialRadius - 2f))
            }
            clipPath(clipDialPath) {
                // Rotate with roll angle around center
                rotate(degrees = -roll, pivot = Offset(cx, cy)) {
                    val pitchPixelsPerDegree = (3.2f * scale).dp.toPx()
                    val horizonY = cy + (pitch * pitchPixelsPerDegree)

                    // Dynamic 0° Horizon Line
                    val horizonWingLen = dialRadius * 0.88f
                    val horizonGap = (20f * scale).dp.toPx()
                    val horizonLineWidth = (2.2f * scale).dp.toPx()

                    // Left horizon bar
                    drawLine(
                        color = horizonLineColor,
                        start = Offset(cx - horizonWingLen, horizonY),
                        end = Offset(cx - horizonGap, horizonY),
                        strokeWidth = horizonLineWidth,
                        cap = StrokeCap.Round
                    )
                    // Right horizon bar
                    drawLine(
                        color = horizonLineColor,
                        start = Offset(cx + horizonGap, horizonY),
                        end = Offset(cx + horizonWingLen, horizonY),
                        strokeWidth = horizonLineWidth,
                        cap = StrokeCap.Round
                    )

                    // Pitch Ladder Rungs (+10°, +20°, -10°, -20°, ±5°, ±15°, ±25°)
                    val rungs = listOf(5, 10, 15, 20, 25, 30, -5, -10, -15, -20, -25, -30)

                    for (deg in rungs) {
                        val rungY = horizonY - (deg * pitchPixelsPerDegree)
                        if (abs(rungY - cy) > dialRadius) continue // Outside dial

                        val isMajorRung = deg % 10 == 0
                        val rungWidth = (if (isMajorRung) 30f * scale else 16f * scale).dp.toPx()
                        val rungGap = (if (isMajorRung) 24f * scale else 20f * scale).dp.toPx()
                        val rungStroke = (if (isMajorRung) 2f * scale else 1.6f * scale).dp.toPx()
                        val rungColor = if (deg > 0) HudCyan.copy(alpha = 0.9f) else HudBorderCyan.copy(alpha = 0.85f)

                        // Left horizontal bar (Solid, crisp line with round caps)
                        drawLine(
                            color = rungColor,
                            start = Offset(cx - rungGap - rungWidth, rungY),
                            end = Offset(cx - rungGap, rungY),
                            strokeWidth = rungStroke,
                            cap = StrokeCap.Round
                        )

                        // Right horizontal bar (Solid, crisp line with round caps)
                        drawLine(
                            color = rungColor,
                            start = Offset(cx + rungGap, rungY),
                            end = Offset(cx + rungGap + rungWidth, rungY),
                            strokeWidth = rungStroke,
                            cap = StrokeCap.Round
                        )

                        // For major 10° rungs, draw solid reference tips pointing toward the horizon line
                        if (isMajorRung) {
                            val tipLen = (5f * scale).dp.toPx()
                            val tipDirY = if (deg > 0) tipLen else -tipLen

                            // Left tip at outer edge
                            drawLine(
                                color = rungColor,
                                start = Offset(cx - rungGap - rungWidth, rungY),
                                end = Offset(cx - rungGap - rungWidth, rungY + tipDirY),
                                strokeWidth = rungStroke,
                                cap = StrokeCap.Round
                            )

                            // Right tip at outer edge
                            drawLine(
                                color = rungColor,
                                start = Offset(cx + rungGap + rungWidth, rungY),
                                end = Offset(cx + rungGap + rungWidth, rungY + tipDirY),
                                strokeWidth = rungStroke,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }

            // 3. FIXED AIRCRAFT WATERLINE / BORESIGHT (Dead Center — fixed to camera body)
            val centerDotRadius = (3.5f * scale).dp.toPx()
            val ringRadius = (13f * scale).dp.toPx()
            val wingGap = (18f * scale).dp.toPx()
            val wingSpan = (34f * scale).dp.toPx()
            val wingDrop = (5f * scale).dp.toPx()
            val waterlineStroke = (2.2f * scale).dp.toPx()

            // Center Boresight Dot
            drawCircle(
                color = boresightColor,
                radius = centerDotRadius
            )

            // Center Optical Ring
            drawCircle(
                color = boresightColor.copy(alpha = 0.85f),
                radius = ringRadius,
                style = Stroke(width = (1.5f * scale).dp.toPx())
            )

            // Left aircraft waterline wing:  ──┐
            val leftWaterline = Path().apply {
                moveTo(cx - wingGap - wingSpan, cy)
                lineTo(cx - wingGap, cy)
                lineTo(cx - wingGap, cy + wingDrop)
            }
            drawPath(leftWaterline, color = boresightColor, style = Stroke(width = waterlineStroke, cap = StrokeCap.Round))

            // Right aircraft waterline wing: ┌──
            val rightWaterline = Path().apply {
                moveTo(cx + wingGap + wingSpan, cy)
                lineTo(cx + wingGap, cy)
                lineTo(cx + wingGap, cy + wingDrop)
            }
            drawPath(rightWaterline, color = boresightColor, style = Stroke(width = waterlineStroke, cap = StrokeCap.Round))

            // Optional Angular Ranging Grid (mil / degree tick marks)
            if (showAngularRangingGrid) {
                val tickWidth = (gridThickness * scale).dp.toPx()
                for (step in 1..3) {
                    val offset = (step * 24 * scale).dp.toPx()
                    val tickLen = (if (step % 2 == 0) 8 * scale else 4 * scale).dp.toPx() + (gridThickness - 1.5f).coerceAtLeast(0f)

                    // Vertical ticks above and below reticle
                    drawLine(gridColor, Offset(cx - tickLen, cy - ringRadius - offset), Offset(cx + tickLen, cy - ringRadius - offset), strokeWidth = tickWidth, cap = StrokeCap.Round)
                    drawLine(gridColor, Offset(cx - tickLen, cy + ringRadius + offset), Offset(cx + tickLen, cy + ringRadius + offset), strokeWidth = tickWidth, cap = StrokeCap.Round)
                }
            }
        }

        // Ephemeris overlay (Sun & Moon position badge if enabled)
        if (showEphemeris && telemetry != null) {
            val sunAz = telemetry.sunAzimuth
            val sunEl = telemetry.sunElevation
            if (sunAz != null && sunEl != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-30 * scale).dp)
                        .background(HudTranslucentDark, RoundedCornerShape(4.dp))
                        .border(1.dp, HudAmber.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "☀️ ${sunAz.toInt()}° / ${if (sunEl >= 0) "+" else ""}${sunEl.toInt()}°",
                        color = HudAmber,
                        fontFamily = FontFamily.Monospace,
                        fontSize = (9 * scale).sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun MotionTelemetryCard(
    telemetry: TelemetryData,
    settings: AppSettings,
    scale: Float,
    modifier: Modifier = Modifier
) {
    val labelSize = (9.5f * scale).sp
    val valueSize = (13f * scale).sp

    Row(
        modifier = modifier
            .background(HudTranslucentDark, RoundedCornerShape((10 * scale).dp))
            .border(1.dp, HudBorderCyan, RoundedCornerShape((10 * scale).dp))
            .padding(horizontal = (8 * scale).dp, vertical = (7 * scale).dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Column 1: GPS Speed
        if (settings.showSpeed) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "GPS SPEED",
                    color = HudCyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = labelSize,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = telemetry.formattedSpeed(settings.unitSystem),
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = valueSize,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        // Column 2: Track Bearing / Course
        if (settings.showTrackBearing) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "TRACK BEARING",
                    color = HudCyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = labelSize,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = telemetry.formattedBearing(),
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = valueSize,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun BottomTelemetryCard(
    telemetry: TelemetryData,
    settings: AppSettings,
    scale: Float,
    modifier: Modifier = Modifier
) {
    val labelSize = (9.5f * scale).sp
    val valueSize = (14.5f * scale).sp
    val compassMode = settings.compassDisplayMode

    Row(
        modifier = modifier
            .background(HudTranslucentDark, RoundedCornerShape((10 * scale).dp))
            .border(1.dp, HudBorderCyan, RoundedCornerShape((10 * scale).dp))
            .padding(horizontal = (8 * scale).dp, vertical = (7 * scale).dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Column 1: Compass Heading (Centered in its own column)
        if (compassMode != CompassDisplayMode.NONE) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "HEADING",
                    color = HudCyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = labelSize,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = telemetry.formattedHeadingWithMode(compassMode),
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = valueSize,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        // Column 2: Elevation Degrees (Centered in its own column)
        if (settings.showElevation) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "ELEVATION",
                    color = HudAmber,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = labelSize,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = telemetryElevationString(telemetry.elevationDegrees),
                    color = HudAmber,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = valueSize,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        // Column 3: Tilt / Roll (Centered in its own column)
        if (settings.showRoll) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "TILT / ROLL",
                    color = HudCyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = labelSize,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = telemetry.formattedRoll(),
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = valueSize,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

private fun telemetryElevationString(elevation: Float): String {
    val sign = if (elevation >= 0f) "+" else ""
    return "$sign%.1f°".format(elevation)
}

// ==========================================
// PREVIEWS
// ==========================================

@Preview(name = "HUD Overlay - Portrait Field State", showBackground = true, backgroundColor = 0xFF101418, widthDp = 412, heightDp = 915)
@Composable
private fun HudOverlayPortraitPreview() {
    TelemetryCameraTheme {
        HudOverlay(
            telemetry = TelemetryData(
                elevationDegrees = -12.4f,
                headingDegrees = 42.0f,
                rollDegrees = 5.2f,
                latitude = 37.8199,
                longitude = -122.4783,
                altitudeMeters = 2450.0,
                pressureHpa = 752.4f,
                temperatureCelsius = 19.5f,
                speedMps = 5.2f,
                declination = 13.1f,
                isTrueNorth = true,
                sunAzimuth = 145.0f,
                sunElevation = 38.2f,
                moonAzimuth = 280.0f,
                moonElevation = -12.4f
            ),
            settings = AppSettings(),
            isLandscape = false
        )
    }
}

@Preview(name = "HUD Overlay - Landscape Field State", showBackground = true, backgroundColor = 0xFF101418, widthDp = 915, heightDp = 412)
@Composable
private fun HudOverlayLandscapePreview() {
    TelemetryCameraTheme {
        HudOverlay(
            telemetry = TelemetryData(
                elevationDegrees = 4.5f,
                headingDegrees = 278.0f,
                rollDegrees = -2.1f,
                latitude = 37.8199,
                longitude = -122.4783,
                altitudeMeters = 2450.0,
                pressureHpa = 752.4f,
                temperatureCelsius = 21.0f,
                declination = 13.1f,
                isTrueNorth = true,
                sunAzimuth = 250.0f,
                sunElevation = 22.0f
            ),
            settings = AppSettings(),
            isLandscape = true
        )
    }
}
