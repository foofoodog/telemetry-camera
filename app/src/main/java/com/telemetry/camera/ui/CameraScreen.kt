package com.telemetry.camera.ui

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import android.widget.Toast
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.telemetry.camera.camera.CameraCaptureManager
import com.telemetry.camera.data.AppSettings
import com.telemetry.camera.data.GalleryRepository
import com.telemetry.camera.data.NorthReference
import com.telemetry.camera.data.TelemetryData
import com.telemetry.camera.sensors.EnvironmentTracker
import com.telemetry.camera.sensors.LocationTracker
import com.telemetry.camera.sensors.OrientationTracker
import com.telemetry.camera.ui.components.HudOverlay
import com.telemetry.camera.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

enum class CaptureMode {
    PHOTO,
    VIDEO
}

@Composable
fun CameraScreen(
    cameraCaptureManager: CameraCaptureManager,
    orientationTracker: OrientationTracker,
    locationTracker: LocationTracker,
    environmentTracker: EnvironmentTracker,
    galleryRepository: GalleryRepository,
    settings: AppSettings,
    onOpenGallery: (Uri?) -> Unit,
    onOpenSettings: () -> Unit,
    onNavigateToSetting: (String) -> Unit = {},
    onToggleHud: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    // Sensors & GPS State
    val orientationState by orientationTracker.orientationFlow.collectAsState()
    val locationState by locationTracker.locationFlow.collectAsState()
    val environmentState by environmentTracker.environmentFlow.collectAsState()

    // 1-second clock tick
    var currentTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            currentTimestamp = System.currentTimeMillis()
        }
    }

    // Consolidated Telemetry
    val currentTelemetry = remember(orientationState, locationState, environmentState, currentTimestamp, settings.northReference) {
        val effectiveLocation = locationState ?: locationTracker.currentLocation
        TelemetryData(
            elevationDegrees = orientationState.elevationDegrees,
            headingDegrees = orientationState.headingDegrees,
            rollDegrees = orientationState.rollDegrees,
            latitude = effectiveLocation?.latitude,
            longitude = effectiveLocation?.longitude,
            altitudeMeters = effectiveLocation?.altitudeMeters,
            accuracyMeters = effectiveLocation?.accuracyMeters,
            speedMps = effectiveLocation?.speedMps,
            bearingDegrees = effectiveLocation?.bearingDegrees,
            declination = effectiveLocation?.declination ?: 0f,
            isTrueNorth = (settings.northReference == NorthReference.TRUE_NORTH),
            sunAzimuth = effectiveLocation?.sunPosition?.azimuth,
            sunElevation = effectiveLocation?.sunPosition?.elevation,
            moonAzimuth = effectiveLocation?.moonPosition?.azimuth,
            moonElevation = effectiveLocation?.moonPosition?.elevation,
            pressureHpa = environmentState.pressureHpa ?: environmentTracker.currentData.pressureHpa,
            temperatureCelsius = environmentState.temperatureCelsius ?: environmentTracker.currentData.temperatureCelsius,
            timestamp = currentTimestamp
        )
    }

    var captureMode by remember { mutableStateOf(CaptureMode.PHOTO) }
    var isCapturing by remember { mutableStateOf(false) }
    var isRecordingVideo by remember { mutableStateOf(false) }
    var recordingDurationSec by remember { mutableStateOf(0) }
    var showFlashEffect by remember { mutableStateOf(false) }
    var lastCapturedUri by remember { mutableStateOf<Uri?>(null) }
    var showGlossary by remember { mutableStateOf(false) }
    var showUserGuide by remember { mutableStateOf(false) }

    if (showGlossary) {
        GlossaryHelpDialog(
            initialCategory = "Camera",
            onNavigateToSetting = { settingKey ->
                showGlossary = false
                onNavigateToSetting(settingKey)
            },
            onOpenUserGuide = {
                showUserGuide = true
            },
            onDismiss = { showGlossary = false }
        )
    }

    if (showUserGuide) {
        UserGuideDialog(
            onDismiss = { showUserGuide = false }
        )
    }

    LaunchedEffect(isRecordingVideo) {
        if (isRecordingVideo) {
            recordingDurationSec = 0
            while (true) {
                delay(1000L)
                recordingDurationSec++
            }
        }
    }

    // Load most recent photo for the gallery thumbnail
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val photos = galleryRepository.getTelemetryPhotos()
            if (photos.isNotEmpty()) {
                lastCapturedUri = photos.first().uri
            }
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Sync camera target rotation with screen orientation
    LaunchedEffect(configuration.orientation) {
        val display = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            try { context.display } catch (e: Exception) { null }
        } else {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            @Suppress("DEPRECATION")
            wm?.defaultDisplay
        }
        display?.rotation?.let { cameraCaptureManager.setTargetRotation(it) }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // 1. Camera Preview
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    setOnTouchListener { _, event ->
                        if (event.action == android.view.MotionEvent.ACTION_UP) {
                            cameraCaptureManager.focusAndMeter(event.x, event.y)
                        }
                        true
                    }
                    coroutineScope.launch {
                        try {
                            cameraCaptureManager.startCamera(lifecycleOwner, this@apply, CameraCaptureManager.Mode.PHOTO)
                        } catch (e: Exception) {
                            Toast.makeText(ctx, "Camera error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. HUD Overlay (Controlled by settings, responsive to landscape)
        HudOverlay(
            telemetry = currentTelemetry,
            settings = settings,
            isLandscape = isLandscape,
            visible = settings.isHudEnabled,
            modifier = Modifier.fillMaxSize()
        )

        // 3. Shutter flash effect
        AnimatedVisibility(
            visible = showFlashEffect,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.White))
        }

        // Action lambda for photo capture
        val onCaptureClick: () -> Unit = {
            if (!isCapturing && !isRecordingVideo) {
                isCapturing = true
                showFlashEffect = true
                coroutineScope.launch {
                    delay(80)
                    showFlashEffect = false
                    try {
                        val telemetryToCapture = currentTelemetry.copy(timestamp = System.currentTimeMillis())
                        val savedUri = cameraCaptureManager.capturePhoto(
                            telemetry = telemetryToCapture,
                            settings = settings,
                            onHardwareCaptureComplete = {
                                // Shutter button unlocks immediately so user can take next photo!
                                isCapturing = false
                            }
                        )
                        lastCapturedUri = savedUri
                        Toast.makeText(context, "Photo saved", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Capture failed: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        isCapturing = false
                    }
                }
            }
        }

        val onShutterClick: () -> Unit = {
            if (captureMode == CaptureMode.PHOTO) {
                onCaptureClick()
            } else {
                if (!isRecordingVideo) {
                    cameraCaptureManager.startVideoRecording(
                        telemetryProvider = { currentTelemetry.copy(timestamp = System.currentTimeMillis()) },
                        unitSystem = settings.unitSystem,
                        onVideoSaved = { uri ->
                            lastCapturedUri = uri
                            Toast.makeText(context, "Video & telemetry subtitle (.srt) saved", Toast.LENGTH_SHORT).show()
                        },
                        onError = { err ->
                            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                            isRecordingVideo = false
                        }
                    )
                    isRecordingVideo = true
                } else {
                    cameraCaptureManager.stopVideoRecording()
                    isRecordingVideo = false
                }
            }
        }

        // Recording indicator at top
        if (isRecordingVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp)
                    .background(HudRed.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "● REC ${String.format(java.util.Locale.US, "%02d:%02d", recordingDurationSec / 60, recordingDurationSec % 60)}",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        // Quick Help Button at Top End (floating subtle HUD button)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 8.dp, end = 12.dp)
        ) {
            IconButton(
                onClick = { showGlossary = true },
                modifier = Modifier
                    .size(38.dp)
                    .background(HudTranslucentDark, CircleShape)
                    .border(1.dp, HudBorderCyan, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = "Glossary & Help",
                    tint = HudCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 4. Controls: Bottom Row in Portrait, Right Column in Landscape
        if (!isLandscape) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 20.dp, start = 28.dp, end = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Photo / Video Mode Selector
                CaptureModeSelector(
                    mode = captureMode,
                    isRecording = isRecordingVideo,
                    onModeSelected = {
                        captureMode = it
                        cameraCaptureManager.setCaptureMode(
                            if (it == CaptureMode.VIDEO) CameraCaptureManager.Mode.VIDEO else CameraCaptureManager.Mode.PHOTO
                        )
                    }
                )

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left half: Gallery Thumbnail Button at Start
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            GalleryThumbnailButton(
                                lastCapturedUri = lastCapturedUri,
                                onClick = { onOpenGallery(lastCapturedUri) }
                            )
                        }

                        // Center spacer reserving exact width of ShutterButton (76.dp)
                        Spacer(modifier = Modifier.width(76.dp))

                        // Right half: Settings at End, Twisty centered in the gap
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 54.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                HudTwistyButton(
                                    isHudEnabled = settings.isHudEnabled,
                                    onClick = onToggleHud
                                )
                            }
                            SettingsButton(
                                onClick = onOpenSettings
                            )
                        }
                    }

                    // Shutter Button anchored dead-center
                    ShutterButton(
                        isCapturing = isCapturing,
                        isVideoMode = captureMode == CaptureMode.VIDEO,
                        isRecording = isRecordingVideo,
                        onClick = onShutterClick
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(end = 20.dp, top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HudTwistyButton(
                        isHudEnabled = settings.isHudEnabled,
                        onClick = onToggleHud
                    )
                    SettingsButton(
                        onClick = onOpenSettings
                    )
                }
                CaptureModeSelector(
                    mode = captureMode,
                    isRecording = isRecordingVideo,
                    onModeSelected = {
                        captureMode = it
                        cameraCaptureManager.setCaptureMode(
                            if (it == CaptureMode.VIDEO) CameraCaptureManager.Mode.VIDEO else CameraCaptureManager.Mode.PHOTO
                        )
                    }
                )
                ShutterButton(
                    isCapturing = isCapturing,
                    isVideoMode = captureMode == CaptureMode.VIDEO,
                    isRecording = isRecordingVideo,
                    onClick = onShutterClick
                )
                GalleryThumbnailButton(
                    lastCapturedUri = lastCapturedUri,
                    onClick = { onOpenGallery(lastCapturedUri) }
                )
            }
        }
    }
}

@Composable
private fun GalleryThumbnailButton(
    lastCapturedUri: Uri?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(54.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(HudTranslucentDark)
            .border(2.dp, HudBorderCyan, RoundedCornerShape(12.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (lastCapturedUri != null) {
            AsyncImage(
                model = lastCapturedUri,
                contentDescription = "Last photo",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Default.Collections,
                contentDescription = "Gallery",
                tint = HudCyan,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun CaptureModeSelector(
    mode: CaptureMode,
    isRecording: Boolean,
    onModeSelected: (CaptureMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(HudTranslucentDark, RoundedCornerShape(16.dp))
            .border(1.dp, HudBorderCyan.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (mode == CaptureMode.PHOTO) HudCyan.copy(alpha = 0.2f) else Color.Transparent)
                .clickable(enabled = !isRecording) { onModeSelected(CaptureMode.PHOTO) }
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = "PHOTO",
                color = if (mode == CaptureMode.PHOTO) HudCyan else Color.LightGray,
                fontWeight = if (mode == CaptureMode.PHOTO) FontWeight.Bold else FontWeight.Normal,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (mode == CaptureMode.VIDEO) HudRed.copy(alpha = 0.25f) else Color.Transparent)
                .clickable(enabled = !isRecording) { onModeSelected(CaptureMode.VIDEO) }
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = "VIDEO",
                color = if (mode == CaptureMode.VIDEO) HudRed else Color.LightGray,
                fontWeight = if (mode == CaptureMode.VIDEO) FontWeight.Bold else FontWeight.Normal,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun ShutterButton(
    isCapturing: Boolean,
    isVideoMode: Boolean = false,
    isRecording: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isVideoMode) {
        if (isRecording) HudRed else Color.White
    } else {
        if (isCapturing) HudAmber else Color.White
    }

    val innerColor = if (isVideoMode) HudRed else if (isCapturing) HudAmber else Color.White
    val innerShape = if (isVideoMode && isRecording) RoundedCornerShape(8.dp) else CircleShape

    Box(
        modifier = modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(HudTranslucentDark)
            .border(4.dp, borderColor, CircleShape)
            .clickable(enabled = !isCapturing) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(if (isVideoMode && isRecording) 34.dp else 62.dp)
                .clip(innerShape)
                .background(innerColor)
        )
    }
}

@Composable
private fun HudTwistyButton(
    isHudEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(HudTranslucentDark)
            .border(1.5.dp, if (isHudEnabled) HudBorderCyan else Slate700, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Layers,
            contentDescription = if (isHudEnabled) "Hide HUD" else "Show HUD",
            tint = if (isHudEnabled) HudCyan else Color.Gray,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(HudTranslucentDark)
            .border(2.dp, HudBorderCyan, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "Settings",
            tint = HudCyan,
            modifier = Modifier.size(26.dp)
        )
    }
}
