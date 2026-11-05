package com.telemetry.camera.ui

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.ui.draw.clip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.exifinterface.media.ExifInterface
import coil.compose.AsyncImage
import com.telemetry.camera.camera.ImageOverlayProcessor
import com.telemetry.camera.data.AppSettings
import com.telemetry.camera.data.DeleteResult
import com.telemetry.camera.data.ExifTelemetryHelper
import com.telemetry.camera.data.GalleryPhoto
import com.telemetry.camera.data.GalleryRepository
import com.telemetry.camera.data.GisExportHelper
import com.telemetry.camera.data.KmzExportItem
import com.telemetry.camera.data.TelemetryData
import com.telemetry.camera.ui.components.HudOverlay
import com.telemetry.camera.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GalleryViewerScreen(
    galleryRepository: GalleryRepository,
    initialPhotoUri: Uri? = null,
    settings: AppSettings = AppSettings(),
    onBack: () -> Unit,
    onNavigateToSetting: (String) -> Unit = {},
    onOpenMap: (Uri?, List<GalleryPhoto>) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isMultiSelectMode by remember { mutableStateOf(false) }
    val selectedPhotos = remember { mutableStateListOf<GalleryPhoto>() }
    var showBatchDeleteConfirmation by remember { mutableStateOf(false) }
    var pendingDeletePhotos by remember { mutableStateOf<List<GalleryPhoto>>(emptyList()) }

    BackHandler {
        if (isMultiSelectMode) {
            isMultiSelectMode = false
            selectedPhotos.clear()
        } else {
            onBack()
        }
    }

    var photos by remember { mutableStateOf<List<GalleryPhoto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showHud by remember { mutableStateOf(true) }
    var showUiChrome by remember { mutableStateOf(true) }
    var isBurningCopy by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var pendingDeletePhoto by remember { mutableStateOf<GalleryPhoto?>(null) }
    var showExportMenu by remember { mutableStateOf(false) }
    var showGlossary by remember { mutableStateOf(false) }
    var showUserGuide by remember { mutableStateOf(false) }

    val deletePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            if (pendingDeletePhotos.isNotEmpty()) {
                val deletedIds = pendingDeletePhotos.map { it.id }.toSet()
                photos = photos.filter { it.id !in deletedIds }
                selectedPhotos.removeAll { it.id in deletedIds }
                Toast.makeText(context, "${pendingDeletePhotos.size} item(s) deleted", Toast.LENGTH_SHORT).show()
                if (photos.isEmpty()) {
                    onBack()
                }
            } else if (pendingDeletePhoto != null) {
                val p = pendingDeletePhoto!!
                photos = photos.filter { it.id != p.id }
                Toast.makeText(context, if (p.isVideo) "Video deleted" else "Photo deleted", Toast.LENGTH_SHORT).show()
                if (photos.isEmpty()) {
                    onBack()
                }
            }
        }
        pendingDeletePhoto = null
        pendingDeletePhotos = emptyList()
    }

    if (showGlossary) {
        GlossaryHelpDialog(
            initialCategory = "Gallery",
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

    // Telemetry cache keyed by Uri
    val telemetryCache = remember { mutableStateMapOf<Uri, TelemetryData?>() }

    // Load photos from repository
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val list = galleryRepository.getTelemetryPhotos().toMutableList()
            if (initialPhotoUri != null && list.none { it.uri == initialPhotoUri }) {
                list.add(0, GalleryPhoto(
                    id = -1L,
                    uri = initialPhotoUri,
                    displayName = "External Image",
                    dateTaken = System.currentTimeMillis()
                ))
            }
            photos = list
            isLoading = false
        }
    }

    if (isLoading) {
        Box(
            modifier = modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = HudCyan)
        }
        return
    }

    if (photos.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "No Telemetry photos captured yet",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = HudCyan)
                ) {
                    Text(
                        text = "RETURN TO CAMERA",
                        color = Color.Black,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        return
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val dropdownMenuWidth = (configuration.screenWidthDp.dp - 24.dp).coerceIn(340.dp, 400.dp)

    // Determine initial page
    val initialPage = remember(photos, initialPhotoUri) {
        val idx = if (initialPhotoUri != null) {
            photos.indexOfFirst { it.uri == initialPhotoUri }
        } else 0
        if (idx >= 0) idx else 0
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { photos.size }
    )

    // Preload telemetry for photos
    LaunchedEffect(photos) {
        withContext(Dispatchers.IO) {
            for (p in photos) {
                if (!telemetryCache.containsKey(p.uri)) {
                    try {
                        val stream = if (p.uri.scheme == "file") {
                            p.uri.path?.let { java.io.FileInputStream(it) }
                        } else {
                            context.contentResolver.openInputStream(p.uri)
                        }
                        stream?.use {
                            telemetryCache[p.uri] = ExifTelemetryHelper.readTelemetryFromInputStream(it)
                        }
                    } catch (e: Exception) {
                        telemetryCache[p.uri] = null
                    }
                }
            }
        }
    }

    val currentPhoto = photos.getOrNull(pagerState.currentPage)
    val currentTelemetry = currentPhoto?.let { telemetryCache[it.uri] }
    val isCurrentPhotoBurned = currentPhoto?.isBurned == true || currentTelemetry?.isBurned == true

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (!isMultiSelectMode) {
            // 1. Horizontal Swipeable Photo Pager (with smooth swiping and beyond-viewport caching)
        HorizontalPager(
            state = pagerState,
            key = { page -> photos.getOrNull(page)?.id ?: page },
            beyondViewportPageCount = 2,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val photo = photos[page]
            val telemetry = telemetryCache[photo.uri]
            val isPhotoBurned = photo.isBurned || telemetry?.isBurned == true

            if (photo.isVideo) {
                VideoPlayerPage(
                    videoUri = photo.uri,
                    isCurrentPage = pagerState.currentPage == page,
                    showUiChrome = showUiChrome,
                    onToggleChrome = { showUiChrome = !showUiChrome },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { showUiChrome = !showUiChrome }
                ) {
                    AsyncImage(
                        model = photo.uri,
                        contentDescription = "Photo ${page + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )

                    // ONLY render live HUD overlay if the photo is NOT already burned!
                    if (!isPhotoBurned && showHud && showUiChrome && telemetry != null) {
                        val scale = settings.displaySize.scaleFactor
                        val galleryHudTopOffset = 48.dp + (16 * scale).dp
                        HudOverlay(
                            telemetry = telemetry,
                            settings = settings.copy(isHudEnabled = true),
                            isLandscape = isLandscape,
                            visible = true,
                            topCardOffset = galleryHudTopOffset,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (!isPhotoBurned && showHud && showUiChrome && telemetry == null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .background(HudTranslucentDark, RoundedCornerShape(8.dp))
                                .border(1.dp, Slate700, RoundedCornerShape(8.dp))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "NO TELEMETRY METADATA",
                                color = Color.LightGray,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // 2. Floating Left Navigation Chevron Button (Tap to slide previous)
        if (showUiChrome && pagerState.currentPage > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
            ) {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .background(HudTranslucentDark, CircleShape)
                        .border(1.5.dp, HudBorderCyan, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous photo",
                        tint = HudCyan,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // 3. Floating Right Navigation Chevron Button (Tap to slide next)
        if (showUiChrome && pagerState.currentPage < photos.size - 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
            ) {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    },
                    modifier = Modifier
                        .size(46.dp)
                        .background(HudTranslucentDark, CircleShape)
                        .border(1.5.dp, HudBorderCyan, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next photo",
                        tint = HudCyan,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // 4. Top Action Bar: Back, Photo Counter, HUD Toggle / Watermark Badge, Share, Delete
        AnimatedVisibility(
            visible = showUiChrome,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 8.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button
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

                // Photo counter badge (e.g. 3 of 12)
                Box(
                    modifier = Modifier
                        .background(HudTranslucentDark, RoundedCornerShape(12.dp))
                        .border(1.dp, HudBorderCyan, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${photos.size}",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                // Right side buttons
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (currentPhoto?.isVideo == true) {
                        Box(
                            modifier = Modifier
                                .background(HudTranslucentDark, RoundedCornerShape(12.dp))
                                .border(1.dp, HudCyan, RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "VIDEO",
                                color = HudCyan,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    } else if (isCurrentPhotoBurned) {
                        // Watermarked badge for already-burned images
                        Box(
                            modifier = Modifier
                                .background(HudTranslucentDark, RoundedCornerShape(12.dp))
                                .border(1.dp, HudAmber, RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "BURNED",
                                color = HudAmber,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    } else {
                        // HUD toggle for clean images
                        IconButton(
                            onClick = { showHud = !showHud },
                            modifier = Modifier
                                .background(HudTranslucentDark, CircleShape)
                                .border(1.dp, if (showHud) HudCyan else Color.Gray, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = "Toggle HUD",
                                tint = if (showHud) HudCyan else Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Multi-Select Grid Button
                    IconButton(
                        onClick = {
                            isMultiSelectMode = true
                            if (currentPhoto != null && !selectedPhotos.contains(currentPhoto)) {
                                selectedPhotos.add(currentPhoto)
                            }
                        },
                        modifier = Modifier
                            .background(HudTranslucentDark, CircleShape)
                            .border(1.dp, HudBorderCyan, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = "Multi-Select Grid",
                            tint = HudCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Tools / Export dropdown
                    Box {
                        IconButton(
                            onClick = { showExportMenu = true },
                            modifier = Modifier
                                .background(HudTranslucentDark, CircleShape)
                                .border(1.dp, HudBorderCyan, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Tools & GIS Export",
                                tint = HudCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false },
                            modifier = Modifier
                                .requiredWidth(dropdownMenuWidth)
                                .background(Slate800)
                        ) {
                            // Field User Guide Option
                            DropdownMenuItem(
                                text = { Text("Field User Guide", color = Color.White, maxLines = 1, softWrap = false) },
                                leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, tint = HudCyan) },
                                onClick = {
                                    showExportMenu = false
                                    showUserGuide = true
                                }
                            )

                            // Glossary & Help Option
                            DropdownMenuItem(
                                text = { Text("Glossary & Help", color = Color.White, maxLines = 1, softWrap = false) },
                                leadingIcon = { Icon(Icons.Default.HelpOutline, contentDescription = null, tint = HudCyan) },
                                onClick = {
                                    showExportMenu = false
                                    showGlossary = true
                                }
                            )

                            if (currentPhoto?.isVideo == true) {
                                DropdownMenuItem(
                                    text = { Text("Play in Video Player", color = Color.White, maxLines = 1, softWrap = false) },
                                    leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = HudCyan) },
                                    onClick = {
                                        showExportMenu = false
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(currentPhoto.uri, "video/*")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Play Video"))
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "No video player installed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }

                            HorizontalDivider(color = Slate700)

                            // 0. Interactive Survey Map (OpenStreetMap)
                            DropdownMenuItem(
                                text = { Text("Interactive Survey Map", color = Color.White, maxLines = 1, softWrap = false) },
                                leadingIcon = { Icon(Icons.Default.Map, contentDescription = null, tint = HudCyan) },
                                onClick = {
                                    showExportMenu = false
                                    val photosWithTelemetry = photos.map { p ->
                                        if (p.telemetry == null && telemetryCache.containsKey(p.uri)) {
                                            p.copy(telemetry = telemetryCache[p.uri])
                                        } else p
                                    }
                                    onOpenMap(currentPhoto?.uri, photosWithTelemetry)
                                }
                            )

                            // 1. View in Map App
                            DropdownMenuItem(
                                text = { Text("Open in Map App", color = Color.White, maxLines = 1, softWrap = false) },
                                leadingIcon = { Icon(Icons.Default.Place, contentDescription = null, tint = HudCyan) },
                                onClick = {
                                    showExportMenu = false
                                    val lat = currentTelemetry?.latitude
                                    val lon = currentTelemetry?.longitude
                                    if (lat != null && lon != null) {
                                        val geoUri = Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(currentPhoto?.displayName ?: "Photo")})")
                                        val mapIntent = Intent(Intent.ACTION_VIEW, geoUri)
                                        try {
                                            context.startActivity(mapIntent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "No map application installed", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "No GPS coordinates in this photo", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )

                            // 2. Export Sidecar JSON
                            DropdownMenuItem(
                                text = { Text("Export Sidecar JSON", color = Color.White, maxLines = 1, softWrap = false) },
                                leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, tint = HudCyan) },
                                onClick = {
                                    showExportMenu = false
                                    if (currentPhoto != null && currentTelemetry != null) {
                                        coroutineScope.launch {
                                            val savedUri = GisExportHelper.exportSidecarJson(
                                                context,
                                                currentPhoto.displayName,
                                                currentTelemetry
                                            )
                                            if (savedUri != null) {
                                                Toast.makeText(context, "Saved companion JSON to Pictures/TelemetryCamera", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, "Failed to save sidecar JSON", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } else {
                                        Toast.makeText(context, "No telemetry to export", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )

                            // 3. Export Google Earth KMZ (Photos + 3D Viewports Bundle)
                            DropdownMenuItem(
                                text = { Text("Export Google Earth KMZ", color = Color.White, maxLines = 1, softWrap = false) },
                                leadingIcon = { Icon(Icons.Default.Layers, contentDescription = null, tint = HudCyan) },
                                onClick = {
                                    showExportMenu = false
                                    coroutineScope.launch {
                                        val validKmzItems = photos.mapNotNull { p ->
                                            if (p.isVideo) return@mapNotNull null
                                            val t = telemetryCache[p.uri]
                                            if (t != null && t.latitude != null && t.longitude != null) {
                                                KmzExportItem(
                                                    displayName = p.displayName,
                                                    photoUri = p.uri,
                                                    telemetry = t
                                                )
                                            } else null
                                        }
                                        if (validKmzItems.isNotEmpty()) {
                                            val savedUri = GisExportHelper.exportKmz(context, validKmzItems)
                                            if (savedUri != null) {
                                                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(savedUri, "application/vnd.google-earth.kmz")
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "application/vnd.google-earth.kmz"
                                                    putExtra(Intent.EXTRA_STREAM, savedUri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                val chooser = Intent.createChooser(viewIntent, "Open or Share Google Earth KMZ").apply {
                                                    putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(shareIntent))
                                                }
                                                context.startActivity(chooser)
                                            } else {
                                                Toast.makeText(context, "Failed to create KMZ bundle", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "No photos with GPS coordinates to export", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )

                            // 4. Export Survey KML (Google Earth)
                            DropdownMenuItem(
                                text = { Text("Export Survey KML", color = Color.White, maxLines = 1, softWrap = false) },
                                leadingIcon = { Icon(Icons.Default.Public, contentDescription = null, tint = HudCyan) },
                                onClick = {
                                    showExportMenu = false
                                    coroutineScope.launch {
                                        val validPhotos = photos.mapNotNull { p ->
                                            val t = telemetryCache[p.uri]
                                            if (t != null && t.latitude != null && t.longitude != null) {
                                                Pair(p.displayName, t)
                                            } else null
                                        }
                                        if (validPhotos.isNotEmpty()) {
                                            val savedUri = GisExportHelper.exportKml(context, validPhotos)
                                            if (savedUri != null) {
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "application/vnd.google-earth.kml+xml"
                                                    putExtra(Intent.EXTRA_STREAM, savedUri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "Share Survey KML"))
                                            }
                                        } else {
                                            Toast.makeText(context, "No photos with GPS coordinates to export", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )

                            // 5. Export Survey GPX
                            DropdownMenuItem(
                                text = { Text("Export Survey GPX", color = Color.White, maxLines = 1, softWrap = false) },
                                leadingIcon = { Icon(Icons.Default.Navigation, contentDescription = null, tint = HudCyan) },
                                onClick = {
                                    showExportMenu = false
                                    coroutineScope.launch {
                                        val validPhotos = photos.mapNotNull { p ->
                                            val t = telemetryCache[p.uri]
                                            if (t != null && t.latitude != null && t.longitude != null) {
                                                Pair(p.displayName, t)
                                            } else null
                                        }
                                        if (validPhotos.isNotEmpty()) {
                                            val savedUri = GisExportHelper.exportGpx(context, validPhotos)
                                            if (savedUri != null) {
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "application/gpx+xml"
                                                    putExtra(Intent.EXTRA_STREAM, savedUri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "Share Survey GPX"))
                                            }
                                        } else {
                                            Toast.makeText(context, "No photos with GPS coordinates to export", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }
                    }

                    // Share button
                    IconButton(
                        onClick = {
                            val photo = currentPhoto ?: return@IconButton
                            val mimeType = if (photo.isVideo) "video/mp4" else "image/jpeg"
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = mimeType
                                putExtra(Intent.EXTRA_STREAM, photo.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, if (photo.isVideo) "Share Telemetry Video" else "Share Telemetry Photo"))
                        },
                        modifier = Modifier
                            .background(HudTranslucentDark, CircleShape)
                            .border(1.dp, HudBorderCyan, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Delete button (prompts for confirmation)
                    IconButton(
                        onClick = {
                            if (currentPhoto != null) {
                                showDeleteConfirmation = true
                            }
                        },
                        modifier = Modifier
                            .background(HudTranslucentDark, CircleShape)
                            .border(1.dp, Color.Red.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color.Red.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // 5. Bottom Action Bar:
        // For Videos -> "OPEN IN VIDEO PLAYER"
        // For Photos -> "BURN & SAVE WATERMARKED COPY"
        if (showUiChrome && currentPhoto?.isVideo == true) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 28.dp),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(currentPhoto.uri, "video/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Play Video"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "No video player installed", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HudTranslucentDark,
                        contentColor = HudCyan
                    ),
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, HudBorderCyan)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Open in Video Player",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "OPEN IN VIDEO PLAYER",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else if (showUiChrome && !isCurrentPhotoBurned && currentTelemetry != null && currentPhoto != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 28.dp),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = {
                        isBurningCopy = true
                        coroutineScope.launch {
                            try {
                                val newUri = burnAndSaveCopy(context, currentPhoto.uri, currentTelemetry, settings)
                                telemetryCache[newUri] = currentTelemetry.copy(isBurned = true)
                                Toast.makeText(context, "Saved copy with burned-in overlay!", Toast.LENGTH_SHORT).show()
                                // Refresh gallery list
                                val updated = galleryRepository.getTelemetryPhotos()
                                photos = updated
                            } catch (e: Exception) {
                                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                            } finally {
                                isBurningCopy = false
                            }
                        }
                    },
                    enabled = !isBurningCopy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HudTranslucentDark,
                        contentColor = HudAmber
                    ),
                    shape = RoundedCornerShape(24.dp),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = !isBurningCopy)
                ) {
                    Icon(
                        imageVector = Icons.Default.SaveAlt,
                        contentDescription = "Burn Copy",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isBurningCopy) "BURNING..." else "BURN & SAVE WATERMARKED COPY",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        } else {
            // ================= MULTI-SELECT GRID MODE =================
            val statusBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(
                    start = 6.dp,
                    top = statusBarTopPadding + 76.dp,
                    end = 6.dp,
                    bottom = navBarBottomPadding + 96.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(photos, key = { it.id }) { photo ->
                    val isSelected = selectedPhotos.any { it.id == photo.id }
                    val telemetry = telemetryCache[photo.uri]
                    val hasGps = telemetry?.latitude != null && telemetry?.longitude != null

                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Slate800)
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) HudCyan else Slate700,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                if (isSelected) {
                                    selectedPhotos.removeAll { it.id == photo.id }
                                } else {
                                    selectedPhotos.add(photo)
                                }
                            }
                    ) {
                        AsyncImage(
                            model = photo.uri,
                            contentDescription = photo.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(HudCyan.copy(alpha = 0.3f))
                            )
                        }

                        // Top-right selection circle
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = HudCyan,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.RadioButtonUnchecked,
                                    contentDescription = "Not selected",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                                )
                            }
                        }

                        // Bottom badges (Video or GPS)
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (photo.isVideo) {
                                Box(
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                                        .border(0.5.dp, HudCyan, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "VIDEO",
                                        color = HudCyan,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else if (hasGps) {
                                Box(
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                                        .border(0.5.dp, HudGreen, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "GPS",
                                        color = HudGreen,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Top Bar in Multi-Select Mode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 8.dp, start = 16.dp, end = 16.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Exit button
                IconButton(
                    onClick = {
                        isMultiSelectMode = false
                        selectedPhotos.clear()
                    },
                    modifier = Modifier
                        .background(HudTranslucentDark, CircleShape)
                        .border(1.dp, HudBorderCyan, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Exit Selection",
                        tint = Color.White
                    )
                }

                // Selection Counter Badge
                Box(
                    modifier = Modifier
                        .background(HudTranslucentDark, RoundedCornerShape(12.dp))
                        .border(1.dp, HudBorderCyan, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${selectedPhotos.size} / ${photos.size} SELECTED",
                        color = if (selectedPhotos.isNotEmpty()) HudCyan else Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                // Right buttons: Select All / Deselect All + Switch to Pager
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (selectedPhotos.size == photos.size) {
                                selectedPhotos.clear()
                            } else {
                                selectedPhotos.clear()
                                selectedPhotos.addAll(photos)
                            }
                        },
                        modifier = Modifier
                            .background(HudTranslucentDark, CircleShape)
                            .border(1.dp, HudBorderCyan, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (selectedPhotos.size == photos.size) Icons.Default.Deselect else Icons.Default.SelectAll,
                            contentDescription = if (selectedPhotos.size == photos.size) "Deselect All" else "Select All",
                            tint = HudCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = { isMultiSelectMode = false },
                        modifier = Modifier
                            .background(HudTranslucentDark, CircleShape)
                            .border(1.dp, HudBorderCyan, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ViewCarousel,
                            contentDescription = "View Photos",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Bottom Action Bar in Multi-Select Mode
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                color = HudTranslucentDark,
                border = androidx.compose.foundation.BorderStroke(1.dp, HudBorderCyan),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val validKmzItems = selectedPhotos.mapNotNull { p ->
                        if (p.isVideo) return@mapNotNull null
                        val t = telemetryCache[p.uri]
                        if (t != null && t.latitude != null && t.longitude != null) {
                            KmzExportItem(displayName = p.displayName, photoUri = p.uri, telemetry = t)
                        } else null
                    }

                    // 0. Survey Map for Selected Photos
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable(enabled = selectedPhotos.isNotEmpty()) {
                            val selectedWithTelemetry = selectedPhotos.map { p ->
                                if (p.telemetry == null && telemetryCache.containsKey(p.uri)) {
                                    p.copy(telemetry = telemetryCache[p.uri])
                                } else p
                            }
                            onOpenMap(selectedWithTelemetry.firstOrNull()?.uri, selectedWithTelemetry)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = "Survey Map",
                            tint = if (selectedPhotos.isNotEmpty()) HudCyan else Color.DarkGray,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "MAP (${selectedPhotos.size})",
                            color = if (selectedPhotos.isNotEmpty()) Color.White else Color.DarkGray,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 1. Export KMZ
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable(enabled = validKmzItems.isNotEmpty()) {
                            coroutineScope.launch {
                                val savedUri = GisExportHelper.exportKmz(context, validKmzItems)
                                if (savedUri != null) {
                                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(savedUri, "application/vnd.google-earth.kmz")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/vnd.google-earth.kmz"
                                        putExtra(Intent.EXTRA_STREAM, savedUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    val chooser = Intent.createChooser(viewIntent, "Open or Share Google Earth KMZ").apply {
                                        putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(shareIntent))
                                    }
                                    context.startActivity(chooser)
                                } else {
                                    Toast.makeText(context, "Failed to create KMZ bundle", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Export KMZ",
                            tint = if (validKmzItems.isNotEmpty()) HudCyan else Color.DarkGray,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "KMZ (${validKmzItems.size})",
                            color = if (validKmzItems.isNotEmpty()) Color.White else Color.DarkGray,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 2. Export KML / GPX (GIS Menu)
                    var showBatchGisMenu by remember { mutableStateOf(false) }
                    Box {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable(enabled = validKmzItems.isNotEmpty()) {
                                showBatchGisMenu = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = "GIS",
                                tint = if (validKmzItems.isNotEmpty()) HudCyan else Color.DarkGray,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "GIS",
                                color = if (validKmzItems.isNotEmpty()) Color.White else Color.DarkGray,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        DropdownMenu(
                            expanded = showBatchGisMenu,
                            onDismissRequest = { showBatchGisMenu = false },
                            modifier = Modifier
                                .requiredWidth(dropdownMenuWidth)
                                .background(Slate800)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export Survey KML (${validKmzItems.size})", color = Color.White, maxLines = 1, softWrap = false) },
                                leadingIcon = { Icon(Icons.Default.Public, null, tint = HudCyan) },
                                onClick = {
                                    showBatchGisMenu = false
                                    coroutineScope.launch {
                                        val items = validKmzItems.map { Pair(it.displayName, it.telemetry) }
                                        val savedUri = GisExportHelper.exportKml(context, items)
                                        if (savedUri != null) {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/vnd.google-earth.kml+xml"
                                                putExtra(Intent.EXTRA_STREAM, savedUri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share Survey KML"))
                                        }
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export Survey GPX (${validKmzItems.size})", color = Color.White, maxLines = 1, softWrap = false) },
                                leadingIcon = { Icon(Icons.Default.Navigation, null, tint = HudCyan) },
                                onClick = {
                                    showBatchGisMenu = false
                                    coroutineScope.launch {
                                        val items = validKmzItems.map { Pair(it.displayName, it.telemetry) }
                                        val savedUri = GisExportHelper.exportGpx(context, items)
                                        if (savedUri != null) {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/gpx+xml"
                                                putExtra(Intent.EXTRA_STREAM, savedUri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share Survey GPX"))
                                        }
                                    }
                                }
                            )
                        }
                    }

                    // 3. Share Selected
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable(enabled = selectedPhotos.isNotEmpty()) {
                            val uris = ArrayList(selectedPhotos.map { it.uri })
                            if (uris.size == 1) {
                                val p = selectedPhotos.first()
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = if (p.isVideo) "video/mp4" else "image/jpeg"
                                    putExtra(Intent.EXTRA_STREAM, p.uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share item"))
                            } else if (uris.size > 1) {
                                val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                    type = "*/*"
                                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share ${uris.size} items"))
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = if (selectedPhotos.isNotEmpty()) Color.White else Color.DarkGray,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "SHARE",
                            color = if (selectedPhotos.isNotEmpty()) Color.White else Color.DarkGray,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 4. Delete Selected
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable(enabled = selectedPhotos.isNotEmpty()) {
                            showBatchDeleteConfirmation = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = if (selectedPhotos.isNotEmpty()) Color.Red else Color.DarkGray,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "DELETE",
                            color = if (selectedPhotos.isNotEmpty()) Color.Red else Color.DarkGray,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Single Photo Delete Confirmation Dialog
        if (showDeleteConfirmation && currentPhoto != null) {
            val photoToDelete = currentPhoto
            val isVideo = photoToDelete.isVideo
            AlertDialog(
                onDismissRequest = { showDeleteConfirmation = false },
                containerColor = Slate800,
                titleContentColor = Color.White,
                textContentColor = Color(0xFFCCCCCC),
                shape = RoundedCornerShape(16.dp),
                title = {
                    Text(
                        text = if (isVideo) "DELETE VIDEO" else "DELETE PHOTO",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFFEF4444)
                    )
                },
                text = {
                    Text(
                        text = if (isVideo) "Are you sure you want to permanently delete this telemetry video and its companion files?"
                        else "Are you sure you want to permanently delete this telemetry photo?",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteConfirmation = false
                            coroutineScope.launch {
                                val result = galleryRepository.deletePhoto(photoToDelete.uri)
                                when (result) {
                                    is DeleteResult.Success -> {
                                        photos = photos.filter { it.id != photoToDelete.id }
                                        Toast.makeText(context, if (isVideo) "Video deleted" else "Photo deleted", Toast.LENGTH_SHORT).show()
                                        if (photos.isEmpty()) {
                                            onBack()
                                        }
                                    }
                                    is DeleteResult.RequiresPermission -> {
                                        pendingDeletePhoto = photoToDelete
                                        deletePermissionLauncher.launch(
                                            IntentSenderRequest.Builder(result.intentSender).build()
                                        )
                                    }
                                    is DeleteResult.Failed -> {
                                        Toast.makeText(context, "Failed to delete item", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFDC2626),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "DELETE",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showDeleteConfirmation = false },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = HudCyan
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, HudBorderCyan),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "CANCEL",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
        }

        // Batch Delete Confirmation Dialog
        if (showBatchDeleteConfirmation && selectedPhotos.isNotEmpty()) {
            val count = selectedPhotos.size
            AlertDialog(
                onDismissRequest = { showBatchDeleteConfirmation = false },
                containerColor = Slate800,
                titleContentColor = Color.White,
                textContentColor = Color(0xFFCCCCCC),
                shape = RoundedCornerShape(16.dp),
                title = {
                    Text(
                        text = "DELETE $count ITEMS",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFFEF4444)
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to permanently delete $count selected telemetry items and companion files?",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showBatchDeleteConfirmation = false
                            val toDelete = selectedPhotos.toList()
                            coroutineScope.launch {
                                val result = galleryRepository.deletePhotos(toDelete.map { it.uri })
                                when (result) {
                                    is DeleteResult.Success -> {
                                        val deletedIds = toDelete.map { it.id }.toSet()
                                        photos = photos.filter { it.id !in deletedIds }
                                        selectedPhotos.clear()
                                        Toast.makeText(context, "$count item(s) deleted", Toast.LENGTH_SHORT).show()
                                        if (photos.isEmpty()) {
                                            onBack()
                                        }
                                    }
                                    is DeleteResult.RequiresPermission -> {
                                        pendingDeletePhotos = toDelete
                                        deletePermissionLauncher.launch(
                                            IntentSenderRequest.Builder(result.intentSender).build()
                                        )
                                    }
                                    is DeleteResult.Failed -> {
                                        Toast.makeText(context, "Failed to delete selected items", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFDC2626),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "DELETE ALL ($count)",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showBatchDeleteConfirmation = false },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = HudCyan
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, HudBorderCyan),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "CANCEL",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun VideoPlayerPage(
    videoUri: Uri,
    isCurrentPage: Boolean,
    showUiChrome: Boolean,
    onToggleChrome: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember(videoUri) { mutableStateOf(false) }
    var videoViewRef by remember(videoUri) { mutableStateOf<VideoView?>(null) }

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage && isPlaying) {
            videoViewRef?.pause()
            isPlaying = false
        }
    }

    DisposableEffect(videoUri) {
        onDispose {
            videoViewRef?.stopPlayback()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable { onToggleChrome() },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoURI(videoUri)
                    setOnPreparedListener { mp ->
                        mp.isLooping = true
                        seekTo(10)
                    }
                    setOnCompletionListener {
                        isPlaying = false
                    }
                    videoViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Large Play / Pause button in center
        if (!isPlaying || showUiChrome) {
            IconButton(
                onClick = {
                    videoViewRef?.let { vv ->
                        if (vv.isPlaying) {
                            vv.pause()
                            isPlaying = false
                        } else {
                            vv.start()
                            isPlaying = true
                        }
                    }
                },
                modifier = Modifier
                    .size(72.dp)
                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                    .border(2.dp, HudCyan, CircleShape)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause Video" else "Play Video",
                    tint = HudCyan,
                    modifier = Modifier.size(42.dp)
                )
            }
        }
    }
}

private suspend fun burnAndSaveCopy(
    context: Context,
    originalUri: Uri,
    telemetry: TelemetryData,
    settings: AppSettings = AppSettings()
): Uri = withContext(Dispatchers.IO) {
    val bitmap = context.contentResolver.openInputStream(originalUri)?.use { stream ->
        BitmapFactory.decodeStream(stream)
    } ?: throw IllegalStateException("Could not decode image")

    // Read source EXIF orientation to ensure upright orientation before watermarking
    val orientation = context.contentResolver.openInputStream(originalUri)?.use { stream ->
        try {
            val exif = ExifInterface(stream)
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    } ?: ExifInterface.ORIENTATION_NORMAL

    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
    }

    val uprightBitmap = if (!matrix.isIdentity) {
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        bitmap.recycle()
        rotated
    } else {
        bitmap
    }

    val watermarked = ImageOverlayProcessor.burnOverlayIntoBitmap(
        uprightBitmap,
        telemetry.copy(isBurned = true),
        settings
    )

    // Save to temp file first so we can write all EXIF telemetry metadata to it
    val tempFile = File.createTempFile("burned_copy_", ".jpg", context.cacheDir)
    try {
        FileOutputStream(tempFile).use { out ->
            watermarked.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }

        // Write full raw telemetry to EXIF (JSON in UserComment + standard GPS tags)
        // With orientation = NORMAL since pixels are already physically upright
        ExifTelemetryHelper.writeTelemetryToExif(
            tempFile,
            telemetry.copy(isBurned = true),
            orientation = ExifInterface.ORIENTATION_NORMAL,
            unitSystem = settings.unitSystem
        )

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(System.currentTimeMillis()))
        val fileName = "TELEMETRY_BURNED_${timeStamp}.jpg"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TelemetryCamera")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            telemetry.latitude?.let { put(MediaStore.Images.Media.LATITUDE, it) }
            telemetry.longitude?.let { put(MediaStore.Images.Media.LONGITUDE, it) }
            put(MediaStore.Images.Media.DATE_TAKEN, telemetry.timestamp)
            put(MediaStore.Images.Media.DESCRIPTION, telemetry.copy(isBurned = true).toJson())
        }

        val resolver = context.contentResolver
        val newUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Failed to create MediaStore entry")

        resolver.openOutputStream(newUri)?.use { out ->
            tempFile.inputStream().use { input ->
                input.copyTo(out)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(newUri, values, null, null)
        }

        newUri
    } finally {
        tempFile.delete()
        if (uprightBitmap != watermarked) {
            watermarked.recycle()
        }
        uprightBitmap.recycle()
    }
}
