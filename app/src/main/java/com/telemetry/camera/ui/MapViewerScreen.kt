package com.telemetry.camera.ui

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.telemetry.camera.data.*
import com.telemetry.camera.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MapTileType(val label: String, val tileSource: ITileSource) {
    STANDARD("Standard OSM", TileSourceFactory.MAPNIK),
    TOPO("Topographic", TileSourceFactory.OpenTopo),
    USGS_SAT("Satellite (USGS)", TileSourceFactory.USGS_SAT)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapViewerScreen(
    photos: List<GalleryPhoto> = emptyList(),
    initialPhotoUri: Uri? = null,
    settings: AppSettings,
    galleryRepository: GalleryRepository? = null,
    onBack: () -> Unit,
    onOpenPhoto: (Uri) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    // Base survey track from gallery photos
    var activeTrack by remember {
        mutableStateOf(SurveyPathParser.fromGalleryPhotos(photos))
    }

    var selectedPoint by remember {
        mutableStateOf<SurveyPoint?>(
            if (initialPhotoUri != null) {
                activeTrack.waypoints.find { it.photoUri == initialPhotoUri }
            } else {
                activeTrack.waypoints.lastOrNull()
            }
        )
    }

    val photoWaypoints = remember(activeTrack) {
        activeTrack.waypoints.filter { it.photoUri != null }
    }
    var previewPhotoIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(photos) {
        val targetPhotos = if (photos.isEmpty() && galleryRepository != null) {
            galleryRepository.getTelemetryPhotos()
        } else {
            photos
        }
        val loaded = SurveyPathParser.loadTrackFromPhotos(context, targetPhotos)
        if (loaded.waypoints.isNotEmpty() || loaded.points.isNotEmpty()) {
            activeTrack = loaded
            if (selectedPoint == null) {
                selectedPoint = if (initialPhotoUri != null) {
                    loaded.waypoints.find { it.photoUri == initialPhotoUri }
                } else {
                    loaded.waypoints.lastOrNull()
                }
            }
        }
    }

    var currentTileType by remember { mutableStateOf(MapTileType.STANDARD) }
    var showTileMenu by remember { mutableStateOf(false) }

    // External file picker launcher for GPX, KML, and KMZ files
    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val parsed = SurveyPathParser.parseFromUri(context, uri)
                if (parsed != null && parsed.points.isNotEmpty()) {
                    activeTrack = parsed
                    selectedPoint = parsed.waypoints.firstOrNull()
                    Toast.makeText(
                        context,
                        "Imported ${parsed.title}: ${parsed.points.size} points (${String.format(Locale.US, "%.1f km", parsed.totalDistanceMeters / 1000.0)})",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(context, "Could not extract GPS track from selected file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Configure osmdroid on first composition
    DisposableEffect(Unit) {
        val prefs = context.getSharedPreferences("${context.packageName}_osm", Context.MODE_PRIVATE)
        Configuration.getInstance().load(context, prefs)
        Configuration.getInstance().userAgentValue = context.packageName
        onDispose { }
    }

    // Retain MapView instance across recompositions
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)

            // Scale Bar Overlay
            val scaleBar = ScaleBarOverlay(this).apply {
                setCentred(false)
                setScaleBarOffset(20, 40)
                setTextSize(26f)
            }
            overlays.add(scaleBar)

            // Compass Overlay
            val compass = CompassOverlay(context, InternalCompassOrientationProvider(context), this).apply {
                enableCompass()
            }
            overlays.add(compass)
        }
    }

    // Bind MapView to Android Activity/Fragment Lifecycle
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_DESTROY -> mapView.onDetach()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    // Update Map Overlays when activeTrack changes
    LaunchedEffect(activeTrack, mapView) {
        // Clear previous path and markers (keep scale bar & compass)
        val nonMarkerOverlays = mapView.overlays.filterIsInstance<ScaleBarOverlay>() +
                mapView.overlays.filterIsInstance<CompassOverlay>()
        mapView.overlays.clear()
        mapView.overlays.addAll(nonMarkerOverlays)

        // 1. Draw connecting survey polyline
        if (activeTrack.points.size >= 2) {
            val polyline = Polyline(mapView).apply {
                setPoints(activeTrack.points)
                outlinePaint.color = HudCyan.toArgb()
                outlinePaint.strokeWidth = 7f
                outlinePaint.isAntiAlias = true
                outlinePaint.strokeCap = Paint.Cap.ROUND
                outlinePaint.strokeJoin = Paint.Join.ROUND
            }
            mapView.overlays.add(polyline)
        }

        // 2. Add photo survey markers
        for (point in activeTrack.waypoints) {
            val heading = point.telemetry?.effectiveHeading
            val marker = Marker(mapView).apply {
                position = point.geoPoint
                title = point.title
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = createSurveyMarkerDrawable(context, heading)
                setOnMarkerClickListener { _, _ ->
                    selectedPoint = point
                    mapView.controller.animateTo(point.geoPoint)
                    true
                }
            }
            mapView.overlays.add(marker)
        }

        mapView.invalidate()

        // 3. Zoom to fit all points or center on initial point
        if (selectedPoint != null) {
            mapView.controller.setZoom(17.5)
            mapView.controller.setCenter(selectedPoint!!.geoPoint)
        } else if (activeTrack.boundingBox != null) {
            mapView.post {
                mapView.zoomToBoundingBox(activeTrack.boundingBox, true, 80)
            }
        } else if (activeTrack.points.isNotEmpty()) {
            mapView.controller.setZoom(16.0)
            mapView.controller.setCenter(activeTrack.points.first())
        }
    }

    // Update tile source when selected
    LaunchedEffect(currentTileType) {
        mapView.setTileSource(currentTileType.tileSource)
        mapView.invalidate()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate900)
    ) {
        // ==========================================
        // 1. INTERACTIVE MAP VIEW (AndroidView)
        // ==========================================
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // ==========================================
        // 2. TOP TACTICAL NAVIGATION BAR
        // ==========================================
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding(),
            color = HudTranslucentDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, HudBorderCyan.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Back Button
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                // Title & Track Stats
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "OSM FIELD SURVEY",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = HudCyan,
                        fontSize = 14.sp,
                        letterSpacing = 1.sp
                    )
                    val distStr = if (settings.unitSystem == UnitSystem.METRIC) {
                        String.format(Locale.US, "%.2f km", activeTrack.totalDistanceMeters / 1000.0)
                    } else {
                        String.format(Locale.US, "%.2f mi", activeTrack.totalDistanceMeters * 0.000621371)
                    }
                    Text(
                        text = "${activeTrack.waypoints.size} Pts • $distStr",
                        fontFamily = FontFamily.Monospace,
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )
                }

                // Map Layer Switcher
                Box {
                    IconButton(onClick = { showTileMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Map Layers",
                            tint = HudCyan
                        )
                    }
                    DropdownMenu(
                        expanded = showTileMenu,
                        onDismissRequest = { showTileMenu = false },
                        modifier = Modifier.background(Slate800)
                    ) {
                        MapTileType.values().forEach { tileType ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = tileType.label,
                                        color = if (tileType == currentTileType) HudCyan else Color.White,
                                        fontWeight = if (tileType == currentTileType) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    if (tileType == currentTileType) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = HudCyan)
                                    }
                                },
                                onClick = {
                                    currentTileType = tileType
                                    showTileMenu = false
                                }
                            )
                        }
                    }
                }

                // Import GPX / KML / KMZ Button
                IconButton(
                    onClick = {
                        importFileLauncher.launch(
                            arrayOf(
                                "application/gpx+xml",
                                "application/vnd.google-earth.kml+xml",
                                "application/vnd.google-earth.kmz",
                                "application/zip",
                                "*/*"
                            )
                        )
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Import GPX / KML",
                        tint = HudAmber
                    )
                }
            }
        }

        // ==========================================
        // 3. FLOATING MAP CONTROLS (Right side)
        // ==========================================
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Zoom In
            FloatingActionButton(
                onClick = { mapView.controller.zoomIn() },
                modifier = Modifier.size(44.dp),
                containerColor = HudTranslucentDark,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In")
            }

            // Zoom Out
            FloatingActionButton(
                onClick = { mapView.controller.zoomOut() },
                modifier = Modifier.size(44.dp),
                containerColor = HudTranslucentDark,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
            }

            // Fit All Survey Bounds
            FloatingActionButton(
                onClick = {
                    if (activeTrack.boundingBox != null) {
                        mapView.zoomToBoundingBox(activeTrack.boundingBox, true, 80)
                    } else {
                        Toast.makeText(context, "No track points to fit", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.size(44.dp),
                containerColor = HudTranslucentDark,
                contentColor = HudCyan,
                shape = CircleShape
            ) {
                Icon(Icons.Default.CropFree, contentDescription = "Fit Survey Bounds")
            }
        }

        // ==========================================
        // 4. SELECTED SURVEY POINT CARD (Bottom)
        // ==========================================
        AnimatedVisibility(
            visible = selectedPoint != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding()
        ) {
            val point = selectedPoint
            if (point != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Slate800.copy(alpha = 0.95f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, HudBorderCyan),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    tint = HudAmber,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = point.title,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (point.photoUri != null) {
                                    IconButton(
                                        onClick = { onOpenPhoto(point.photoUri) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PhotoLibrary,
                                            contentDescription = "Open in Gallery",
                                            tint = HudCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { selectedPoint = null },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = Color.LightGray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Enlarged Thumbnail Preview with expand badge
                            if (point.photoUri != null) {
                                Box(
                                    modifier = Modifier
                                        .size(92.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, HudBorderCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        .clickable {
                                            val idx = photoWaypoints.indexOfFirst {
                                                it.photoUri == point.photoUri || it.id == point.id
                                            }.let { if (it >= 0) it else 0 }
                                            previewPhotoIndex = idx
                                        }
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(point.photoUri)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Photo thumbnail",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    // Expand badge in bottom corner
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(3.dp)
                                            .background(HudTranslucentDark, RoundedCornerShape(4.dp))
                                            .border(1.dp, HudBorderCyan.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                            .padding(2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Fullscreen,
                                            contentDescription = "Full Preview",
                                            tint = HudCyan,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }

                            // Telemetry Metrics
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                val t = point.telemetry
                                val latStr = String.format(Locale.US, "%.5f° %s", Math.abs(point.geoPoint.latitude), if (point.geoPoint.latitude >= 0) "N" else "S")
                                val lonStr = String.format(Locale.US, "%.5f° %s", Math.abs(point.geoPoint.longitude), if (point.geoPoint.longitude >= 0) "E" else "W")

                                Text(
                                    text = "GPS: $latStr, $lonStr",
                                    fontFamily = FontFamily.Monospace,
                                    color = HudCyan,
                                    fontSize = 11.sp
                                )

                                if (t != null) {
                                    Text(
                                        text = "HDG: ${t.formattedHeading()} | PITCH: ${t.formattedElevation()}",
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.LightGray,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = "ALT: ${t.formattedAltitude(settings.unitSystem)} | ROLL: ${t.formattedRoll()}",
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.LightGray,
                                        fontSize = 11.sp
                                    )
                                } else {
                                    Text(
                                        text = "ALT: ${String.format(Locale.US, "%.1f m", point.geoPoint.altitude)}",
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.LightGray,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 5. IN-MAP FULL PHOTO & TELEMETRY PREVIEW
        // ==========================================
        AnimatedVisibility(
            visible = previewPhotoIndex != null && photoWaypoints.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val validIndex = previewPhotoIndex ?: 0
            val exitPreview: () -> Unit = {
                val viewedPoint = previewPhotoIndex?.let { photoWaypoints.getOrNull(it) } ?: selectedPoint
                if (viewedPoint != null) {
                    selectedPoint = viewedPoint
                    mapView.controller.animateTo(viewedPoint.geoPoint)
                }
                previewPhotoIndex = null
            }

            BackHandler {
                exitPreview()
            }

            MapPhotoPreviewOverlay(
                photoWaypoints = photoWaypoints,
                initialIndex = validIndex,
                settings = settings,
                onIndexChanged = { newIdx ->
                    previewPhotoIndex = newIdx
                    val targetPt = photoWaypoints.getOrNull(newIdx)
                    if (targetPt != null) {
                        selectedPoint = targetPt
                    }
                },
                onClose = exitPreview,
                onInspectInGallery = onOpenPhoto
            )
        }
    }
}

/**
 * Creates a custom vector bitmap drawable representing a survey point with a directional heading indicator.
 */
private fun createSurveyMarkerDrawable(context: Context, headingDegrees: Float?): Drawable {
    val sizePx = 64
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val cx = sizePx / 2f
    val cy = sizePx / 2f
    val radius = 18f

    // Outer cyber-slate circle
    val bgPaint = Paint().apply {
        color = android.graphics.Color.argb(220, 15, 23, 42)
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    canvas.drawCircle(cx, cy, radius, bgPaint)

    // Border
    val borderPaint = Paint().apply {
        color = android.graphics.Color.rgb(6, 182, 212) // HudCyan
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }
    canvas.drawCircle(cx, cy, radius, borderPaint)

    // Center Amber Pin Dot
    val dotPaint = Paint().apply {
        color = android.graphics.Color.rgb(251, 191, 36) // HudAmber
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    canvas.drawCircle(cx, cy, 6f, dotPaint)

    // Directional Heading Arrow if heading is available
    if (headingDegrees != null) {
        val arrowPaint = Paint().apply {
            color = android.graphics.Color.rgb(251, 191, 36) // HudAmber
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        canvas.save()
        canvas.rotate(headingDegrees, cx, cy)

        val arrowPath = Path().apply {
            moveTo(cx, cy - radius - 8f) // Tip pointing forward
            lineTo(cx - 7f, cy - radius + 2f)
            lineTo(cx + 7f, cy - radius + 2f)
            close()
        }
        canvas.drawPath(arrowPath, arrowPaint)
        canvas.restore()
    }

    return BitmapDrawable(context.resources, bitmap)
}

/**
 * Full-screen overlay allowing high-resolution inspection of survey captures with full 6-DoF telemetry HUD,
 * with horizontal swiping/stepping between all photos along the survey track.
 */
@Composable
private fun MapPhotoPreviewOverlay(
    photoWaypoints: List<SurveyPoint>,
    initialIndex: Int,
    settings: AppSettings,
    onIndexChanged: (Int) -> Unit,
    onClose: () -> Unit,
    onInspectInGallery: (Uri) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (photoWaypoints.size - 1).coerceAtLeast(0)),
        pageCount = { photoWaypoints.size }
    )

    LaunchedEffect(pagerState.currentPage) {
        onIndexChanged(pagerState.currentPage)
    }

    val currentPoint = photoWaypoints.getOrNull(pagerState.currentPage)
    val telemetry = currentPoint?.telemetry

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Horizontal Swipeable Photos Pager
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val point = photoWaypoints[page]
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (point.photoUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(point.photoUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = point.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // 2. Navigation Chevrons
        if (photoWaypoints.size > 1) {
            if (pagerState.currentPage > 0) {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp)
                        .background(HudTranslucentDark, CircleShape)
                        .border(1.dp, HudBorderCyan.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous Photo",
                        tint = HudCyan
                    )
                }
            }

            if (pagerState.currentPage < photoWaypoints.size - 1) {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp)
                        .background(HudTranslucentDark, CircleShape)
                        .border(1.dp, HudBorderCyan.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next Photo",
                        tint = HudCyan
                    )
                }
            }
        }

        // 3. Top Action Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding(),
            color = HudTranslucentDark,
            border = BorderStroke(1.dp, HudBorderCyan.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .background(Slate800, CircleShape)
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Preview",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = "POINT ${pagerState.currentPage + 1} OF ${photoWaypoints.size}",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = HudCyan,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = currentPoint?.title ?: "",
                        fontFamily = FontFamily.Monospace,
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = {
                        currentPoint?.photoUri?.let(onInspectInGallery)
                    },
                    modifier = Modifier
                        .background(Slate800, CircleShape)
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Inspect in Gallery",
                        tint = HudCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 4. Bottom HUD Telemetry Panel
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding(),
            color = HudTranslucentDark,
            border = BorderStroke(1.dp, HudBorderCyan),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Coordinates Row
                val lat = currentPoint?.geoPoint?.latitude ?: 0.0
                val lon = currentPoint?.geoPoint?.longitude ?: 0.0
                val latDir = if (lat >= 0) "N" else "S"
                val lonDir = if (lon >= 0) "E" else "W"
                val decCoord = String.format(Locale.US, "%.5f° %s, %.5f° %s", Math.abs(lat), latDir, Math.abs(lon), lonDir)
                val dmsCoord = telemetry?.formattedCoordinatesDms()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "GPS: $decCoord",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = HudCyan,
                        fontSize = 12.sp
                    )
                    if (telemetry != null) {
                        Text(
                            text = telemetry.formattedDateTime(),
                            fontFamily = FontFamily.Monospace,
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                    }
                }

                if (dmsCoord != null && dmsCoord != decCoord) {
                    Text(
                        text = "DMS: $dmsCoord",
                        fontFamily = FontFamily.Monospace,
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )
                }

                HorizontalDivider(color = Slate700)

                // Telemetry Metrics Grid
                if (telemetry != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "HEADING",
                                fontFamily = FontFamily.Monospace,
                                color = HudAmber,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = telemetry.formattedHeading(),
                                fontFamily = FontFamily.Monospace,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column {
                            Text(
                                text = "PITCH",
                                fontFamily = FontFamily.Monospace,
                                color = HudAmber,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = telemetry.formattedElevation(),
                                fontFamily = FontFamily.Monospace,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column {
                            Text(
                                text = "ROLL",
                                fontFamily = FontFamily.Monospace,
                                color = HudAmber,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = telemetry.formattedRoll(),
                                fontFamily = FontFamily.Monospace,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column {
                            Text(
                                text = "ALTITUDE",
                                fontFamily = FontFamily.Monospace,
                                color = HudAmber,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = telemetry.formattedAltitude(settings.unitSystem),
                                fontFamily = FontFamily.Monospace,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Environmental metrics if available
                    val baro = telemetry.formattedPressure(settings.unitSystem)
                    val temp = telemetry.formattedTemperature(settings.unitSystem)
                    if (baro != "Baro: --" || temp != "Temp: --") {
                        Text(
                            text = "$baro  •  $temp",
                            fontFamily = FontFamily.Monospace,
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                    }
                } else {
                    val altStr = if (currentPoint != null) String.format(Locale.US, "%.1f m", currentPoint.geoPoint.altitude) else "--"
                    Text(
                        text = "ALTITUDE: $altStr",
                        fontFamily = FontFamily.Monospace,
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
