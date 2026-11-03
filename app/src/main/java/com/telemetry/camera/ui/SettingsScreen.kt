package com.telemetry.camera.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale
import com.telemetry.camera.data.AppSettings
import com.telemetry.camera.data.CompassDisplayMode
import com.telemetry.camera.data.GpsDisplayMode
import com.telemetry.camera.data.HudDisplaySize
import com.telemetry.camera.data.NorthReference
import com.telemetry.camera.data.UnitSystem
import com.telemetry.camera.ui.theme.*

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    onBack: () -> Unit,
    targetSetting: String? = null,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val scrollState = rememberScrollState()
    var showGlossary by remember { mutableStateOf(false) }
    var showUserGuide by remember { mutableStateOf(false) }
    var activeTargetSetting by remember(targetSetting) { mutableStateOf(targetSetting) }
    var highlightedKey by remember { mutableStateOf<String?>(null) }
    val sectionPositions = remember { mutableStateMapOf<String, Int>() }
    var columnTopY by remember { mutableStateOf(0f) }

    if (showGlossary) {
        GlossaryHelpDialog(
            initialCategory = "Settings",
            onNavigateToSetting = { activeTargetSetting = it },
            onOpenUserGuide = { showUserGuide = true },
            onDismiss = { showGlossary = false }
        )
    }

    if (showUserGuide) {
        UserGuideDialog(
            onDismiss = { showUserGuide = false }
        )
    }

    // Scroll to target setting card when requested
    LaunchedEffect(activeTargetSetting, sectionPositions.size) {
        activeTargetSetting?.let { target ->
            val targetKey = when (target) {
                "hud_overlay", "burn_in", "watermark_badge" -> "general_controls"
                "declination", "ephemeris", "angular_grid" -> "field_survey"
                else -> target
            }
            val pos = sectionPositions[targetKey]
            if (pos != null) {
                scrollState.animateScrollTo(pos)
                highlightedKey = targetKey
                delay(2500L)
                highlightedKey = null
                activeTargetSetting = null
            }
        }
    }

    fun cardPositionModifier(key: String): Modifier = Modifier.onGloballyPositioned { coords ->
        val cardYInRoot = coords.positionInRoot().y
        sectionPositions[key] = (scrollState.value + (cardYInRoot - columnTopY).toInt()).coerceAtLeast(0)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Slate900)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 12.dp, start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            // Header: Back button + Title + Glossary Help Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .background(HudTranslucentDark, CircleShape)
                        .border(1.dp, HudBorderCyan, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = "TELEMETRY SETTINGS",
                    color = HudCyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    letterSpacing = 1.sp,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = { showGlossary = true },
                    modifier = Modifier
                        .background(HudTranslucentDark, CircleShape)
                        .border(1.dp, HudBorderCyan, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.HelpOutline,
                        contentDescription = "Glossary & Help",
                        tint = HudCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Scrollable Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .onGloballyPositioned { layoutCoordinates ->
                        columnTopY = layoutCoordinates.positionInRoot().y
                    },
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Section 1: HUD & Burn Master Switches
                SettingsCard(
                    title = "GENERAL CONTROLS",
                    isHighlighted = highlightedKey == "general_controls",
                    modifier = cardPositionModifier("general_controls")
                ) {
                    SettingsSwitchRow(
                        title = "HUD Overlay",
                        subtitle = "Display live HUD telemetry on screen",
                        checked = settings.isHudEnabled,
                        onCheckedChange = { onSettingsChanged(settings.copy(isHudEnabled = it)) }
                    )

                    HorizontalDivider(color = Slate700, modifier = Modifier.padding(vertical = 8.dp))

                    SettingsSwitchRow(
                        title = "Burn-in by Default",
                        subtitle = "Stamp telemetry overlay permanently onto photo pixels",
                        checked = settings.isBurnOverlayEnabled,
                        onCheckedChange = { onSettingsChanged(settings.copy(isBurnOverlayEnabled = it)) }
                    )

                    HorizontalDivider(color = Slate700, modifier = Modifier.padding(vertical = 8.dp))

                    SettingsSwitchRow(
                        title = "App Watermark Badge",
                        subtitle = "Stamp a subtle '⌖ TELEMETRY' badge on photos to identify metadata-compatible images in other apps",
                        checked = settings.showWatermarkBadge,
                        onCheckedChange = { onSettingsChanged(settings.copy(showWatermarkBadge = it)) }
                    )
                }

                // Section 2: HUD Display Size (Small, Medium, Large)
                SettingsCard(
                    title = "HUD DISPLAY SIZE",
                    isHighlighted = highlightedKey == "display_size",
                    modifier = cardPositionModifier("display_size")
                ) {
                    Text(
                        text = "Scale the camera overlay elements for visibility or compactness:",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    HudDisplaySize.values().forEach { size ->
                        val description = when (size) {
                            HudDisplaySize.SMALL -> "Small (Compact, minimal screen obstruction)"
                            HudDisplaySize.MEDIUM -> "Medium (Standard balanced layout)"
                            HudDisplaySize.LARGE -> "Large (High-visibility text and reticle)"
                        }
                        SettingsRadioRow(
                            title = description,
                            selected = settings.displaySize == size,
                            onClick = { onSettingsChanged(settings.copy(displaySize = size)) }
                        )
                    }
                }

                // Section 3: Units of Measurement
                SettingsCard(
                    title = "UNITS OF MEASUREMENT",
                    isHighlighted = highlightedKey == "units",
                    modifier = cardPositionModifier("units")
                ) {
                    Text(
                        text = "Select preferred units for speed, distance, elevation, and temperature:",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    UnitSystem.values().forEach { system ->
                        SettingsRadioRow(
                            title = system.label,
                            selected = settings.unitSystem == system,
                            onClick = { onSettingsChanged(settings.copy(unitSystem = system)) }
                        )
                    }
                }

                // Section 4: Time & Location Display
                SettingsCard(
                    title = "TIME & ALTITUDE",
                    isHighlighted = highlightedKey == "time_altitude",
                    modifier = cardPositionModifier("time_altitude")
                ) {
                    SettingsSwitchRow(
                        title = "Date & Timestamp",
                        subtitle = "Display live date and clock time on overlay",
                        checked = settings.showTime,
                        onCheckedChange = { onSettingsChanged(settings.copy(showTime = it)) }
                    )

                    HorizontalDivider(color = Slate700, modifier = Modifier.padding(vertical = 8.dp))

                    val altUnit = if (settings.unitSystem == UnitSystem.METRIC) "meters" else "feet"
                    SettingsSwitchRow(
                        title = "Altitude & Accuracy",
                        subtitle = "Display elevation above sea level ($altUnit)",
                        checked = settings.showAltitude,
                        onCheckedChange = { onSettingsChanged(settings.copy(showAltitude = it)) }
                    )
                }

                // Section 5: GPS Coordinate Variety
                SettingsCard(
                    title = "GPS DISPLAY FORMAT",
                    isHighlighted = highlightedKey == "gps_format",
                    modifier = cardPositionModifier("gps_format")
                ) {
                    Text(
                        text = "Select which coordinate format appears on the overlay:",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    GpsDisplayMode.values().forEach { mode ->
                        SettingsRadioRow(
                            title = mode.label,
                            selected = settings.gpsDisplayMode == mode,
                            onClick = { onSettingsChanged(settings.copy(gpsDisplayMode = mode)) }
                        )
                    }
                }

                // Section 6: Compass Heading Format
                SettingsCard(
                    title = "COMPASS HEADING FORMAT",
                    isHighlighted = highlightedKey == "compass_format",
                    modifier = cardPositionModifier("compass_format")
                ) {
                    Text(
                        text = "Select how direction and azimuth are displayed:",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    CompassDisplayMode.values().forEach { mode ->
                        SettingsRadioRow(
                            title = mode.label,
                            selected = settings.compassDisplayMode == mode,
                            onClick = { onSettingsChanged(settings.copy(compassDisplayMode = mode)) }
                        )
                    }
                }

                // Section 7: Reticle & Horizon Level
                SettingsCard(
                    title = "RETICLE & HORIZON LEVEL",
                    isHighlighted = highlightedKey == "reticle_horizon",
                    modifier = cardPositionModifier("reticle_horizon")
                ) {
                    SettingsSwitchRow(
                        title = "Horizon Reticle & Wings",
                        subtitle = "Show center crosshair and horizon leveling lines",
                        checked = settings.showHorizonReticle,
                        onCheckedChange = { onSettingsChanged(settings.copy(showHorizonReticle = it)) }
                    )

                    HorizontalDivider(color = Slate700, modifier = Modifier.padding(vertical = 8.dp))

                    SettingsSwitchRow(
                        title = "Elevation Pitch Angle",
                        subtitle = "Show camera vertical tilt pitch in degrees",
                        checked = settings.showElevation,
                        onCheckedChange = { onSettingsChanged(settings.copy(showElevation = it)) }
                    )

                    HorizontalDivider(color = Slate700, modifier = Modifier.padding(vertical = 8.dp))

                    SettingsSwitchRow(
                        title = "Horizon Roll / Tilt",
                        subtitle = "Show camera horizon leveling roll in degrees",
                        checked = settings.showRoll,
                        onCheckedChange = { onSettingsChanged(settings.copy(showRoll = it)) }
                    )
                }

                // Section 8: Environment & Atmospheric
                SettingsCard(
                    title = "ENVIRONMENT & ATMOSPHERIC",
                    isHighlighted = highlightedKey == "environment",
                    modifier = cardPositionModifier("environment")
                ) {
                    val baroUnit = if (settings.unitSystem == UnitSystem.METRIC) "hPa" else "inHg"
                    val tempUnit = if (settings.unitSystem == UnitSystem.METRIC) "°C" else "°F"

                    SettingsSwitchRow(
                        title = "Barometric Pressure",
                        subtitle = "Display atmospheric pressure ($baroUnit)",
                        checked = settings.showPressure,
                        onCheckedChange = { onSettingsChanged(settings.copy(showPressure = it)) }
                    )

                    HorizontalDivider(color = Slate700, modifier = Modifier.padding(vertical = 8.dp))

                    SettingsSwitchRow(
                        title = "Temperature",
                        subtitle = "Display temperature ($tempUnit)",
                        checked = settings.showTemperature,
                        onCheckedChange = { onSettingsChanged(settings.copy(showTemperature = it)) }
                    )
                }

                // Section 9: Motion & Movement
                SettingsCard(
                    title = "MOTION & MOVEMENT",
                    isHighlighted = highlightedKey == "motion",
                    modifier = cardPositionModifier("motion")
                ) {
                    val speedUnit = if (settings.unitSystem == UnitSystem.METRIC) "km/h" else "mph"

                    SettingsSwitchRow(
                        title = "GPS Movement Speed",
                        subtitle = "Display velocity over ground ($speedUnit)",
                        checked = settings.showSpeed,
                        onCheckedChange = { onSettingsChanged(settings.copy(showSpeed = it)) }
                    )

                    HorizontalDivider(color = Slate700, modifier = Modifier.padding(vertical = 8.dp))

                    SettingsSwitchRow(
                        title = "Track Bearing Course",
                        subtitle = "Display travel course direction over ground",
                        checked = settings.showTrackBearing,
                        onCheckedChange = { onSettingsChanged(settings.copy(showTrackBearing = it)) }
                    )
                }

                // Section 10: Advanced & Field Survey
                SettingsCard(
                    title = "ADVANCED & FIELD SURVEY",
                    isHighlighted = highlightedKey == "field_survey",
                    modifier = cardPositionModifier("field_survey")
                ) {
                    Text(
                        text = "North Reference:",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    NorthReference.values().forEach { ref ->
                        SettingsRadioRow(
                            title = ref.label,
                            selected = settings.northReference == ref,
                            onClick = { onSettingsChanged(settings.copy(northReference = ref)) }
                        )
                    }

                    HorizontalDivider(color = Slate700, modifier = Modifier.padding(vertical = 8.dp))

                    SettingsSwitchRow(
                        title = "Sun & Moon Ephemeris",
                        subtitle = "Display live solar/lunar coordinates and reticle markers",
                        checked = settings.showSunMoonEphemeris,
                        onCheckedChange = { onSettingsChanged(settings.copy(showSunMoonEphemeris = it)) }
                    )

                    HorizontalDivider(color = Slate700, modifier = Modifier.padding(vertical = 8.dp))

                    SettingsSwitchRow(
                        title = "Angular Ranging Grid",
                        subtitle = "Show milliradian / degree tick marks on horizon reticle",
                        checked = settings.showAngularRangingGrid,
                        onCheckedChange = { onSettingsChanged(settings.copy(showAngularRangingGrid = it)) }
                    )

                    if (settings.showAngularRangingGrid) {
                        Spacer(modifier = Modifier.height(10.dp))

                        // Live Reticle Tick Preview
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Slate900, RoundedCornerShape(8.dp))
                                .border(1.dp, Slate700, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Grid Preview",
                                color = Color.LightGray,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp
                            )
                            Canvas(modifier = Modifier.size(width = 90.dp, height = 26.dp)) {
                                val midY = size.height / 2f
                                val wingColor = HudAmber
                                val gridColor = Color(settings.rangingGridColor)
                                val stroke = settings.rangingGridThickness.dp.toPx()
                                // Horizon line
                                drawLine(wingColor, Offset(0f, midY), Offset(size.width, midY), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                                // 3 vertical ticks
                                val offsets = listOf(15f, 45f, 75f)
                                for ((idx, x) in offsets.withIndex()) {
                                    val tickH = if (idx == 1) 9.dp.toPx() else 5.dp.toPx()
                                    drawLine(gridColor, Offset(x, midY - tickH), Offset(x, midY + tickH), strokeWidth = stroke, cap = StrokeCap.Round)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Color Presets (16 Curated Tactical Field Colors)
                        Text(
                            text = "Grid Color (16 Tactical Presets):",
                            color = Color.LightGray,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        val palette = listOf(
                            0xFFF59E0BL, // Amber Orange (Default, matches wings)
                            0xFF00E5FFL, // Cyber Cyan
                            0xFFFACC15L, // Neon Yellow
                            0xFFEF4444L, // Signal Red
                            0xFF84CC16L, // Lime Green
                            0xFF10B981L, // Emerald Green
                            0xFF3B82F6L, // Electric Blue
                            0xFF8B5CF6L, // Vivid Purple
                            0xFFEC4899L, // Hot Pink
                            0xFFF43F5EL, // Coral Rose
                            0xFFFFFFFFL, // Optical White
                            0xFF94A3B8L, // Muted Slate
                            0xFFFB923CL, // Safety Orange
                            0xFFEAB308L, // Golden Sun
                            0xFF14B8A6L, // Tactical Teal
                            0xFF38BDF8L  // Sky Blue
                        )

                        // 2 rows of 8 swatches
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (row in palette.chunked(8)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    for (colorVal in row) {
                                        val isSelected = settings.rangingGridColor == colorVal
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(Color(colorVal))
                                                .border(
                                                    width = if (isSelected) 2.5.dp else 1.dp,
                                                    color = if (isSelected) Color.White else Color.Black.copy(alpha = 0.5f),
                                                    shape = CircleShape
                                                )
                                                .clickable { onSettingsChanged(settings.copy(rangingGridColor = colorVal)) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = if (colorVal == 0xFFFFFFFFL || colorVal == 0xFFFACC15L) Color.Black else Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 256-Color Spectrum Hue Slider
                        val currentHsv = remember(settings.rangingGridColor) {
                            val hsv = FloatArray(3)
                            android.graphics.Color.colorToHSV(settings.rangingGridColor.toInt(), hsv)
                            hsv[0]
                        }

                        Text(
                            text = "Fine-Tune Hue (256-Color Spectrum):",
                            color = Color.LightGray,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )

                        Slider(
                            value = currentHsv,
                            onValueChange = { hue ->
                                val rgb = android.graphics.Color.HSVToColor(floatArrayOf(hue, 1.0f, 1.0f))
                                onSettingsChanged(settings.copy(rangingGridColor = (rgb.toLong() and 0xFFFFFFFFL)))
                            },
                            valueRange = 0f..360f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(settings.rangingGridColor),
                                activeTrackColor = HudCyan,
                                inactiveTrackColor = Slate700
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Grid Thickness Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Grid Tick Thickness:",
                                color = Color.LightGray,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp
                            )
                            Text(
                                text = "${String.format(Locale.US, "%.1f", settings.rangingGridThickness)} dp",
                                color = HudAmber,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }

                        Slider(
                            value = settings.rangingGridThickness,
                            onValueChange = { onSettingsChanged(settings.copy(rangingGridThickness = it)) },
                            valueRange = 1.0f..6.0f,
                            steps = 9,
                            colors = SliderDefaults.colors(
                                thumbColor = HudAmber,
                                activeTrackColor = HudAmber,
                                inactiveTrackColor = Slate700
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Section 6: Field Documentation & User Guide
                SettingsCard(
                    title = "FIELD DOCUMENTATION & USER GUIDE",
                    isHighlighted = highlightedKey == "documentation",
                    modifier = cardPositionModifier("documentation")
                ) {
                    Text(
                        text = "Complete offline phototheodolite manual, sensor telemetry specifications, and 11 field surveying workflows:",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Button(
                        onClick = { showUserGuide = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Slate900),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, HudCyan),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = HudCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "OPEN FIELD USER GUIDE",
                            color = HudCyan,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    modifier: Modifier = Modifier,
    isHighlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val borderColor = if (isHighlighted) HudCyan else HudBorderCyan.copy(alpha = 0.35f)
    val borderWidth = if (isHighlighted) 2.dp else 1.dp
    val containerBg = if (isHighlighted) Slate800.copy(alpha = 0.95f) else Slate800

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(containerBg, RoundedCornerShape(12.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(
            text = title,
            color = HudAmber,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        content()
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Text(
                text = subtitle,
                color = Color.LightGray,
                fontSize = 11.sp
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = HudCyan,
                uncheckedThumbColor = Color.LightGray,
                uncheckedTrackColor = Slate700
            )
        )
    }
}

@Composable
private fun SettingsRadioRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = HudCyan,
                unselectedColor = Color.Gray
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            color = if (selected) Color.White else Color.LightGray,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ==========================================
// PREVIEWS
// ==========================================

@Preview(name = "Settings Screen - Default", showBackground = true, backgroundColor = 0xFF0B0F14, heightDp = 1000)
@Composable
private fun SettingsScreenPreview() {
    TelemetryCameraTheme {
        SettingsScreen(
            settings = AppSettings(),
            onSettingsChanged = {},
            onBack = {}
        )
    }
}
