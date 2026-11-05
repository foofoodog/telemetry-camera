package com.telemetry.camera

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.telemetry.camera.camera.CameraCaptureManager
import com.telemetry.camera.data.GalleryPhoto
import com.telemetry.camera.data.GalleryRepository
import com.telemetry.camera.data.SettingsManager
import com.telemetry.camera.sensors.EnvironmentTracker
import com.telemetry.camera.sensors.LocationTracker
import com.telemetry.camera.sensors.OrientationTracker
import com.telemetry.camera.ui.CameraScreen
import com.telemetry.camera.ui.GalleryViewerScreen
import com.telemetry.camera.ui.GlossaryHelpDialog
import com.telemetry.camera.ui.MapViewerScreen
import com.telemetry.camera.ui.SettingsScreen
import com.telemetry.camera.ui.UserGuideDialog
import com.telemetry.camera.ui.theme.HudAmber
import com.telemetry.camera.ui.theme.HudCyan
import com.telemetry.camera.ui.theme.Slate800
import com.telemetry.camera.ui.theme.Slate900
import com.telemetry.camera.ui.theme.TelemetryCameraTheme

enum class AppNavScreen {
    CAMERA,
    GALLERY,
    SETTINGS,
    MAP
}

class MainActivity : ComponentActivity() {

    private lateinit var cameraCaptureManager: CameraCaptureManager
    private lateinit var orientationTracker: OrientationTracker
    private lateinit var locationTracker: LocationTracker
    private lateinit var environmentTracker: EnvironmentTracker
    private lateinit var galleryRepository: GalleryRepository
    private lateinit var settingsManager: SettingsManager

    private var incomingImageUri by mutableStateOf<Uri?>(null)
    private var targetScreenName by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIntent(intent)

        cameraCaptureManager = CameraCaptureManager(this)
        orientationTracker = OrientationTracker(this)
        locationTracker = LocationTracker(this)
        environmentTracker = EnvironmentTracker(this)
        galleryRepository = GalleryRepository(this)
        settingsManager = SettingsManager(this)

        setContent {
            TelemetryCameraTheme {
                MainContent(
                    cameraCaptureManager = cameraCaptureManager,
                    orientationTracker = orientationTracker,
                    locationTracker = locationTracker,
                    environmentTracker = environmentTracker,
                    galleryRepository = galleryRepository,
                    settingsManager = settingsManager,
                    incomingUri = incomingImageUri,
                    initialScreen = targetScreenName
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        if (intent == null) return
        targetScreenName = intent.getStringExtra("screen")
        val uri: Uri? = when (intent.action) {
            android.content.Intent.ACTION_VIEW -> intent.data
            android.content.Intent.ACTION_SEND -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM) as? Uri
                }
            }
            else -> null
        }
        if (uri != null) {
            incomingImageUri = uri
        }
    }
}

@Composable
fun MainContent(
    cameraCaptureManager: CameraCaptureManager,
    orientationTracker: OrientationTracker,
    locationTracker: LocationTracker,
    environmentTracker: EnvironmentTracker,
    galleryRepository: GalleryRepository,
    settingsManager: SettingsManager,
    incomingUri: Uri? = null,
    initialScreen: String? = null
) {
    val context = LocalContext.current

    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        )
    } else {
        arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }

    fun checkAllPermissions(): Boolean {
        return requiredPermissions.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    var hasPermissions by remember { mutableStateOf(checkAllPermissions()) }
    var currentScreen by remember {
        mutableStateOf(
            when (initialScreen) {
                "settings" -> AppNavScreen.SETTINGS
                "gallery" -> AppNavScreen.GALLERY
                "map" -> AppNavScreen.MAP
                else -> if (incomingUri != null) AppNavScreen.GALLERY else AppNavScreen.CAMERA
            }
        )
    }
    var targetSettingKey by remember { mutableStateOf<String?>(null) }
    var showDirectGlossary by remember { mutableStateOf(initialScreen == "glossary") }
    var showDirectUserGuide by remember { mutableStateOf(initialScreen == "guide" || initialScreen == "userguide") }
    var initialGalleryUri by remember { mutableStateOf(incomingUri) }
    var selectedMapPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var mapPhotos by remember { mutableStateOf<List<GalleryPhoto>>(emptyList()) }

    if (showDirectGlossary) {
        GlossaryHelpDialog(
            initialCategory = "All",
            onNavigateToSetting = { settingKey ->
                showDirectGlossary = false
                targetSettingKey = settingKey
                currentScreen = AppNavScreen.SETTINGS
            },
            onOpenUserGuide = {
                showDirectUserGuide = true
            },
            onDismiss = { showDirectGlossary = false }
        )
    }

    if (showDirectUserGuide) {
        UserGuideDialog(
            onDismiss = { showDirectUserGuide = false }
        )
    }

    LaunchedEffect(incomingUri) {
        if (incomingUri != null) {
            initialGalleryUri = incomingUri
            currentScreen = AppNavScreen.GALLERY
        }
    }

    LaunchedEffect(initialScreen) {
        when (initialScreen) {
            "settings" -> currentScreen = AppNavScreen.SETTINGS
            "gallery" -> currentScreen = AppNavScreen.GALLERY
            "map" -> currentScreen = AppNavScreen.MAP
            "glossary" -> showDirectGlossary = true
            "guide", "userguide" -> showDirectUserGuide = true
            else -> {}
        }
    }

    val appSettings by settingsManager.settingsFlow.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermissions = results[Manifest.permission.CAMERA] == true
        if (results[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            locationTracker.start()
        }
    }

    // Lifecycle-aware sensor management
    DisposableEffect(hasPermissions) {
        if (hasPermissions) {
            orientationTracker.start()
            locationTracker.start()
            environmentTracker.start()
        }
        onDispose {
            orientationTracker.stop()
            locationTracker.stop()
            environmentTracker.stop()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Slate900
    ) {
        if (!hasPermissions) {
            PermissionRequestScreen(
                onRequestPermissions = {
                    permissionLauncher.launch(requiredPermissions)
                }
            )
        } else {
            when (currentScreen) {
                AppNavScreen.CAMERA -> {
                    CameraScreen(
                        cameraCaptureManager = cameraCaptureManager,
                        orientationTracker = orientationTracker,
                        locationTracker = locationTracker,
                        environmentTracker = environmentTracker,
                        galleryRepository = galleryRepository,
                        settings = appSettings,
                        onOpenGallery = { uri ->
                            initialGalleryUri = uri
                            currentScreen = AppNavScreen.GALLERY
                        },
                        onOpenSettings = {
                            currentScreen = AppNavScreen.SETTINGS
                        },
                        onNavigateToSetting = { settingKey ->
                            targetSettingKey = settingKey
                            currentScreen = AppNavScreen.SETTINGS
                        },
                        onToggleHud = {
                            settingsManager.updateSettings(appSettings.copy(isHudEnabled = !appSettings.isHudEnabled))
                        }
                    )
                }
                AppNavScreen.GALLERY -> {
                    GalleryViewerScreen(
                        galleryRepository = galleryRepository,
                        initialPhotoUri = initialGalleryUri,
                        settings = appSettings,
                        onBack = {
                            currentScreen = AppNavScreen.CAMERA
                            initialGalleryUri = null
                        },
                        onNavigateToSetting = { settingKey ->
                            targetSettingKey = settingKey
                            currentScreen = AppNavScreen.SETTINGS
                        },
                        onOpenMap = { uri, photos ->
                            selectedMapPhotoUri = uri
                            mapPhotos = photos
                            currentScreen = AppNavScreen.MAP
                        }
                    )
                }
                AppNavScreen.SETTINGS -> {
                    SettingsScreen(
                        settings = appSettings,
                        targetSetting = targetSettingKey,
                        onSettingsChanged = { settingsManager.updateSettings(it) },
                        onBack = {
                            targetSettingKey = null
                            currentScreen = AppNavScreen.CAMERA
                        }
                    )
                }
                AppNavScreen.MAP -> {
                    MapViewerScreen(
                        photos = mapPhotos,
                        initialPhotoUri = selectedMapPhotoUri,
                        settings = appSettings,
                        galleryRepository = galleryRepository,
                        onBack = {
                            currentScreen = AppNavScreen.GALLERY
                        },
                        onOpenPhoto = { uri ->
                            initialGalleryUri = uri
                            currentScreen = AppNavScreen.GALLERY
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionRequestScreen(
    onRequestPermissions: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate900)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "TELEMETRY CAMERA",
                color = HudCyan,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "This app requires Camera and GPS Location permissions to display real-time elevation angle, compass heading, coordinates, and burn telemetry onto your photos.",
                color = Color.LightGray,
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Slate800,
                border = BorderStroke(1.dp, HudAmber.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "RECOMMENDED PERMISSION CHOICES:",
                        color = HudAmber,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Camera: \"While using the app\" for persistent viewfinder access.\n" +
                                "• Location: \"Precise\" for accurate GPS coordinates & speed.\n" +
                                "• Photos: \"Allow all\" to browse & export all captures in the gallery.",
                        color = Color.LightGray,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = onRequestPermissions,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = HudCyan)
            ) {
                Text(
                    text = "GRANT PERMISSIONS",
                    color = Color.Black,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
