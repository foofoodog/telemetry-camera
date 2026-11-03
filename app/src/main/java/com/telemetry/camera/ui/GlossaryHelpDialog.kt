package com.telemetry.camera.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.telemetry.camera.ui.theme.*

data class GlossaryEntry(
    val title: String,
    val category: String, // "Camera", "Gallery", "Settings"
    val summary: String,
    val details: String,
    val technicalFormula: String? = null,
    val settingTarget: String? = null
)

val GLOSSARY_ITEMS = listOf(
    // === 1. CAMERA SCREEN ===
    GlossaryEntry(
        title = "Live HUD Overlay",
        category = "Camera",
        summary = "Toggles aerospace heads-up display overlay on live camera viewfinder.",
        details = "Displays real-time telemetry banners, horizon reticle wings, and motion cards directly on the camera preview. Tap the twisty icon button next to Settings to instantly toggle the HUD on or off. Hiding the HUD does not stop telemetry from being recorded into photo EXIF metadata.",
        settingTarget = "hud_overlay"
    ),
    GlossaryEntry(
        title = "Photo / Video Mode",
        category = "Camera",
        summary = "Switches between high-resolution still capture and video recording.",
        details = "In Photo mode, CameraX optimizes preview and capture for low-light clarity. In Video mode, binds video recording and outputs synchronized .SRT subtitle telemetry files alongside the .MP4."
    ),
    GlossaryEntry(
        title = "Horizon Reticle & Pitch Ladder",
        category = "Camera",
        summary = "Aviation-style boresight crosshair with dynamic pitch wings.",
        details = "Displays aircraft-style horizon reference wings that rotate dynamically with device pitch and roll, maintaining true horizontal orientation regardless of perspective.",
        settingTarget = "reticle_horizon"
    ),
    GlossaryEntry(
        title = "Elevation (Pitch Angle)",
        category = "Camera",
        summary = "Optical line-of-sight vertical tilt from -90° (nadir) to +90° (zenith).",
        details = "Calibrated directly to the camera's optical line-of-sight axis using rotation vector and gravity sensor fusion. 0.0° indicates pointing directly at the true horizon.",
        technicalFormula = "Elevation = -asin(2 * (w*x - y*z))",
        settingTarget = "reticle_horizon"
    ),
    GlossaryEntry(
        title = "Heading / Azimuth",
        category = "Camera",
        summary = "Horizontal compass direction (0°–359°) with 16-point cardinal labels.",
        details = "Displays the direction the camera lens is pointed. Formatted with nautical cardinal labels (e.g., 293° WNW). Can reference Magnetic North or True Geographic North.",
        technicalFormula = "Azimuth = (atan2(Ey, Ex) + 360) % 360",
        settingTarget = "compass_format"
    ),
    GlossaryEntry(
        title = "Tilt / Roll Angle",
        category = "Camera",
        summary = "Left/right lateral banking angle for camera leveling.",
        details = "Measures lateral banking angle around the lens optical axis. Used for architectural leveling, panorama alignment, and engineering inspection.",
        technicalFormula = "Roll = atan2(2*(w*z + x*y), 1 - 2*(y^2 + z^2))",
        settingTarget = "reticle_horizon"
    ),
    GlossaryEntry(
        title = "GPS Coordinates (Decimal & DMS)",
        category = "Camera",
        summary = "Live latitude and longitude displayed in Decimal GIS and Nautical DMS.",
        details = "Real-time GNSS location formatted simultaneously as Decimal Degrees (37.81993° N, 122.47825° W) and Degrees, Minutes, Seconds (37°49'11.7\"N, 122°28'41.7\"W).",
        settingTarget = "gps_format"
    ),
    GlossaryEntry(
        title = "True Altitude & Precision",
        category = "Camera",
        summary = "Height above Mean Sea Level (MSL) with horizontal precision radius.",
        details = "Retrieved from fused GPS/GNSS receiver in feet or meters. The accuracy metric (e.g. ±9.8 ft) indicates the 68% confidence radius of the position fix.",
        settingTarget = "time_altitude"
    ),
    GlossaryEntry(
        title = "Barometer & Ambient Temp",
        category = "Camera",
        summary = "Live atmospheric air pressure (inHg/hPa) and ambient temperature.",
        details = "Sampled directly from hardware MEMS barometric pressure sensor and thermal sensors for field meteorology and altimeter verification.",
        settingTarget = "environment"
    ),
    GlossaryEntry(
        title = "Sun & Moon Ephemeris",
        category = "Camera",
        summary = "Astronomical celestial coordinates for the Sun and Moon.",
        details = "Calculates exact solar and lunar azimuth (compass direction) and elevation (altitude angle above/below horizon) based on your coordinates and current UTC timestamp.",
        settingTarget = "ephemeris"
    ),
    GlossaryEntry(
        title = "Angular Ranging Grid",
        category = "Camera",
        summary = "Milliradian and degree reticle graduations for distance estimation.",
        details = "Displays calibrated milliradian and degree tick marks on the reticle crosshairs. Enables optical stadia rangefinding: height = distance * tan(angle).",
        settingTarget = "angular_grid"
    ),
    GlossaryEntry(
        title = "Tap-to-Focus & Auto-Exposure",
        category = "Camera",
        summary = "Locks optical focus and meters exposure at tapped spot.",
        details = "Tapping anywhere on the viewfinder preview commands CameraX to run a 3A metering scan (Auto-Focus, Auto-Exposure, Auto-White Balance) centered on that point."
    ),

    // === 2. GALLERY SCREEN ===
    GlossaryEntry(
        title = "Metadata HUD Playback (Layers)",
        category = "Gallery",
        summary = "Renders recorded telemetry overlay over clean photos.",
        details = "Reconstructs the full cyan HUD overlay dynamically from the photo's saved EXIF metadata. Operates 100% independently of the live camera viewfinder HUD switch."
    ),
    GlossaryEntry(
        title = "Burn & Save Watermarked Copy",
        category = "Gallery",
        summary = "Rasterizes HUD overlay permanently into a duplicate JPEG.",
        details = "Takes any clean photo and burns the cyan telemetry graphics into its physical pixels at full resolution (saving as TELEMETRY_BURNED_<timestamp>.jpg) while leaving the original untouched.",
        settingTarget = "burn_in"
    ),
    GlossaryEntry(
        title = "Export Sidecar JSON",
        category = "Gallery",
        summary = "Exports structured telemetry JSON file alongside photo.",
        details = "Generates a <photo_name>.json sidecar file in Pictures/TelemetryCamera/ containing all raw numeric sensor values, timestamps, and calibration parameters for GIS analysis."
    ),
    GlossaryEntry(
        title = "Export Google Earth KMZ",
        category = "Gallery",
        summary = "Bundles all photos and 3D virtual viewports into a single Google Earth archive.",
        details = "Creates a standalone Keyhole Markup Zip (.kmz) file bundling captured high-res photos with 6-DoF 3D camera pose tags (<Camera>). When opened in Google Earth, clicking a pin displays the photo inside an interactive telemetry popup, and double-clicking reorients the 3D globe to the photographer's exact physical viewpoint and tilt angle."
    ),
    GlossaryEntry(
        title = "Export Survey KML",
        category = "Gallery",
        summary = "Exports Google Earth KML placemark file with telemetry callouts.",
        details = "Generates standard KML files that open directly in Google Earth, pinpointing photo location with HTML popup cards showing attitude, altitude, and weather."
    ),
    GlossaryEntry(
        title = "Export Survey GPX",
        category = "Gallery",
        summary = "Exports Garmin-compatible GPS waypoint file.",
        details = "Generates GPS Exchange Format (.gpx) waypoints compatible with Garmin outdoor GPS devices, QGIS, ArcGIS, Gaia GPS, and OsmAnd."
    ),
    GlossaryEntry(
        title = "Open in Map App",
        category = "Gallery",
        summary = "Launches default mapping app directly at photo coordinates.",
        details = "Sends geo: intent with coordinates to Google Maps, OsmAnd, or any installed mapping app for navigation or satellite imagery review."
    ),
    GlossaryEntry(
        title = "Swipe Pager & Chevrons",
        category = "Gallery",
        summary = "Smooth horizontal swiping between photos with cache preloading.",
        details = "Swipe left/right or tap floating arrow chevrons to navigate photos with beyond-viewport caching. Tap image center for full-screen immersive view."
    ),

    // === 3. SETTINGS SCREEN ===
    GlossaryEntry(
        title = "HUD Overlay Master Switch",
        category = "Settings",
        summary = "Master toggle to show or hide HUD graphics on camera viewfinder.",
        details = "When disabled, keeps the camera view completely clean and unobstructed while maintaining full background sensor recording in photo metadata. Can also be toggled directly from the live viewfinder using the twisty button next to Settings.",
        settingTarget = "hud_overlay"
    ),
    GlossaryEntry(
        title = "Burn-in by Default",
        category = "Settings",
        summary = "Default photo capture saving behavior.",
        details = "When OFF (default), captures pristine clean photos with 100% telemetry stored in metadata. When ON, rasterizes HUD graphics permanently into every captured photo.",
        settingTarget = "burn_in"
    ),
    GlossaryEntry(
        title = "App Watermark Badge",
        category = "Settings",
        summary = "Stamps '⌖ TELEMETRY' in top-right corner of captured photos.",
        details = "Subtle corner badge that identifies metadata-compatible photos when browsing in external photo apps like Google Photos, Google Drive, or Android Files.",
        settingTarget = "watermark_badge"
    ),
    GlossaryEntry(
        title = "HUD Display Size",
        category = "Settings",
        summary = "Scales HUD typography, cards, and reticle proportions.",
        details = "Select Small (0.85×) for minimal viewfinder obstruction, Medium (1.0×, default) for balanced tactical readability, or Large (1.15×) for high-visibility survey work.",
        settingTarget = "display_size"
    ),
    GlossaryEntry(
        title = "GPS Coordinate Formats",
        category = "Settings",
        summary = "Select visible coordinate display formats.",
        details = "Choose between Both (Decimal & DMS, default), Decimal Only, DMS Only, or Hidden / Off.",
        settingTarget = "gps_format"
    ),
    GlossaryEntry(
        title = "True North vs. Magnetic North",
        category = "Settings",
        summary = "Selects compass azimuth reference pole.",
        details = "Magnetic North uses device hardware compass poles. True North uses GPS coordinates and the NOAA World Magnetic Model (WMM) to apply local magnetic declination.",
        settingTarget = "declination"
    ),
    GlossaryEntry(
        title = "Compass Modes",
        category = "Settings",
        summary = "Configures compass heading presentation on cockpit card.",
        details = "Select Degrees & Cardinal (e.g. 293° WNW), Degrees Only (293°), Cardinal Only (WNW), or Hidden.",
        settingTarget = "compass_format"
    ),
    GlossaryEntry(
        title = "Sun & Moon Ephemeris Switch",
        category = "Settings",
        summary = "Enables real-time solar and lunar position indicators on HUD.",
        details = "Computes astronomical ephemeris for azimuth and elevation of Sun and Moon.",
        settingTarget = "ephemeris"
    ),
    GlossaryEntry(
        title = "Angular Ranging Grid Switch",
        category = "Settings",
        summary = "Enables milliradian and degree tick marks on center reticle.",
        details = "Overlays optical stadia rangefinding graduations on crosshair.",
        settingTarget = "angular_grid"
    )
)

val GLOSSARY_CATEGORIES = listOf("Camera", "Gallery", "Settings")

@Composable
fun GlossaryHelpDialog(
    initialCategory: String? = null,
    onNavigateToSetting: ((String) -> Unit)? = null,
    onOpenUserGuide: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val initialCat = if (initialCategory?.equals("all", ignoreCase = true) == true) null else initialCategory
    var selectedCategory by remember(initialCategory) { mutableStateOf<String?>(initialCat) }

    val filteredItems = remember(searchQuery, selectedCategory) {
        GLOSSARY_ITEMS.filter { item ->
            val matchesCategory = selectedCategory == null || item.category == selectedCategory
            val matchesSearch = searchQuery.isBlank() ||
                item.title.contains(searchQuery, ignoreCase = true) ||
                item.summary.contains(searchQuery, ignoreCase = true) ||
                item.details.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            color = Slate900,
            border = androidx.compose.foundation.BorderStroke(1.dp, HudBorderCyan)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = "Glossary",
                            tint = HudCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "GLOSSARY",
                            color = HudCyan,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            letterSpacing = 1.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onOpenUserGuide != null) {
                            Button(
                                onClick = {
                                    onDismiss()
                                    onOpenUserGuide()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Slate800),
                                border = BorderStroke(1.dp, HudBorderCyan),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = HudCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "USER GUIDE",
                                    color = HudCyan,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(32.dp)
                                .background(HudTranslucentDark, CircleShape)
                                .border(1.dp, HudBorderCyan, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 56.dp),
                    placeholder = {
                        Text(
                            text = "Search toggles, metrics, formulas...",
                            color = Slate500,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = HudCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HudCyan,
                        unfocusedBorderColor = Slate700,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = HudCyan
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 4 Category Filter Pills: [ ALL ] [ CAMERA ] [ GALLERY ] [ SETTINGS ]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CategoryPill(
                        label = "ALL",
                        isSelected = selectedCategory == null,
                        modifier = Modifier.weight(1f),
                        onClick = { selectedCategory = null }
                    )
                    GLOSSARY_CATEGORIES.forEach { cat ->
                        CategoryPill(
                            label = cat.uppercase(),
                            isSelected = selectedCategory == cat,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                selectedCategory = if (selectedCategory == cat) null else cat
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Item List with Section Headers
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    if (selectedCategory == null && searchQuery.isBlank()) {
                        // Grouped with section headers
                        GLOSSARY_CATEGORIES.forEach { category ->
                            val sectionItems = filteredItems.filter { it.category == category }
                            if (sectionItems.isNotEmpty()) {
                                item(key = "header_$category") {
                                    GlossarySectionHeader(
                                        title = when (category) {
                                            "Camera" -> "CAMERA VIEWFINDER"
                                            "Gallery" -> "GALLERY VIEWER"
                                            "Settings" -> "SETTINGS & CONFIG"
                                            else -> category.uppercase()
                                        }
                                    )
                                }
                                items(sectionItems, key = { it.title }) { entry ->
                                    GlossaryCard(entry, onNavigateToSetting, onDismiss)
                                }
                            }
                        }
                    } else {
                        // Flat filtered list
                        items(filteredItems, key = { it.title }) { entry ->
                            GlossaryCard(entry, onNavigateToSetting, onDismiss)
                        }
                    }

                    if (filteredItems.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No matching glossary terms found",
                                    color = Color.Gray,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryPill(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) HudCyan.copy(alpha = 0.25f) else HudTranslucentDark)
            .border(1.dp, if (isSelected) HudCyan else Slate700, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) HudCyan else Color.LightGray,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 11.5.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun GlossarySectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = Slate700)
        Text(
            text = "  $title  ",
            color = HudCyan,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.sp
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = Slate700)
    }
}

@Composable
private fun GlossaryCard(
    entry: GlossaryEntry,
    onNavigateToSetting: ((String) -> Unit)? = null,
    onDismiss: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = HudTranslucentDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (expanded) HudCyan else Slate700)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = entry.title,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (entry.settingTarget != null && onNavigateToSetting != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(HudCyan.copy(alpha = 0.15f))
                                .border(1.dp, HudBorderCyan, CircleShape)
                                .clickable {
                                    onDismiss()
                                    onNavigateToSetting(entry.settingTarget)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Open in Settings",
                                tint = HudCyan,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .background(Slate800, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = entry.category.uppercase(),
                        color = HudAmber,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = entry.summary,
                color = Color.LightGray,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.5.sp,
                lineHeight = 15.sp
            )

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    HorizontalDivider(color = Slate800, modifier = Modifier.padding(bottom = 6.dp))

                    Text(
                        text = entry.details,
                        color = Color(0xFFCFD8DC),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )

                    if (entry.technicalFormula != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Slate900, RoundedCornerShape(6.dp))
                                .border(1.dp, Slate700, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "MATH: ${entry.technicalFormula}",
                                color = HudCyan,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// PREVIEWS
// ==========================================

@Preview(name = "Glossary Dialog - Full", showBackground = true, backgroundColor = 0xFF0B0F14)
@Composable
private fun GlossaryHelpDialogPreview() {
    TelemetryCameraTheme {
        GlossaryHelpDialog(
            initialCategory = "ALL",
            onDismiss = {},
            onNavigateToSetting = {}
        )
    }
}

@Preview(name = "Glossary Entry Card - Expanded", showBackground = true, backgroundColor = 0xFF0B0F14, widthDp = 380)
@Composable
private fun GlossaryCardExpandedPreview() {
    TelemetryCameraTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            GlossaryCard(
                entry = GlossaryEntry(
                    title = "Angular Ranging Grid",
                    category = "Camera",
                    summary = "Tactical mil-dot / radian scale reticle ticks for visual distance and target estimation.",
                    details = "Superimposes subtle horizontal and vertical pitch/bearing angular measurement ticks spaced at exact milliradian intervals across the camera center.",
                    technicalFormula = "Distance = (Target_Height_m * 1000) / Mils_Subtended",
                    settingTarget = "ranging_grid"
                ),
                onNavigateToSetting = {}
            )
        }
    }
}
