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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

            // 2. CENTER RETICLE / PITCH HORIZON LADDER
            if (settings.showHorizonReticle) {
                CenterReticle(
                    roll = telemetry.rollDegrees,
                    scale = scale,
                    showAngularRangingGrid = settings.showAngularRangingGrid,
                    gridColor = Color(settings.rangingGridColor),
                    gridThickness = settings.rangingGridThickness,
                    showEphemeris = settings.showSunMoonEphemeris,
                    telemetry = telemetry,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size((220 * scale).dp)
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
    scale: Float,
    showAngularRangingGrid: Boolean = false,
    gridColor: Color = HudAmber,
    gridThickness: Float = 2.0f,
    showEphemeris: Boolean = false,
    telemetry: TelemetryData? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .rotate(-roll)
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f

            // Center target circle
            drawCircle(
                color = HudBorderCyan,
                radius = (24 * scale).dp.toPx(),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = (2 * scale).dp.toPx())
            )

            // Center crosshair dot
            drawCircle(
                color = HudAmber,
                radius = (3 * scale).dp.toPx()
            )

            // Horizon crosshair wings
            val wingLength = (32 * scale).dp.toPx()
            val gap = (30 * scale).dp.toPx()

            // Left wing
            drawLine(
                color = HudAmber,
                start = Offset(cx - gap - wingLength, cy),
                end = Offset(cx - gap, cy),
                strokeWidth = (2 * scale).dp.toPx(),
                cap = StrokeCap.Round
            )

            // Right wing
            drawLine(
                color = HudAmber,
                start = Offset(cx + gap, cy),
                end = Offset(cx + gap + wingLength, cy),
                strokeWidth = (2 * scale).dp.toPx(),
                cap = StrokeCap.Round
            )

            // Optional Angular Ranging Grid (mil / degree tick marks)
            if (showAngularRangingGrid) {
                val tickWidth = (gridThickness * scale).dp.toPx()
                for (step in 1..3) {
                    val offset = (step * 20 * scale).dp.toPx()
                    val tickLen = (if (step % 2 == 0) 8 else 4) * scale + (gridThickness - 1.5f).coerceAtLeast(0f)

                    // Horizontal ticks on wings
                    drawLine(gridColor, Offset(cx - gap - offset, cy - tickLen), Offset(cx - gap - offset, cy + tickLen), strokeWidth = tickWidth, cap = StrokeCap.Round)
                    drawLine(gridColor, Offset(cx + gap + offset, cy - tickLen), Offset(cx + gap + offset, cy + tickLen), strokeWidth = tickWidth, cap = StrokeCap.Round)

                    // Vertical ticks above and below reticle
                    drawLine(gridColor, Offset(cx - tickLen, cy - gap - offset), Offset(cx + tickLen, cy - gap - offset), strokeWidth = tickWidth, cap = StrokeCap.Round)
                    drawLine(gridColor, Offset(cx - tickLen, cy + gap + offset), Offset(cx + tickLen, cy + gap + offset), strokeWidth = tickWidth, cap = StrokeCap.Round)
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
                        .offset(y = (-32 * scale).dp)
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
