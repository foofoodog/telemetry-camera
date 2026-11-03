package com.telemetry.camera.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.LifecycleOwner
import com.telemetry.camera.data.ExifTelemetryHelper
import com.telemetry.camera.data.TelemetryData
import com.telemetry.camera.data.UnitSystem
import kotlinx.coroutines.*
import android.Manifest
import android.content.pm.PackageManager
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import java.util.concurrent.TimeUnit

class CameraCaptureManager(private val context: Context) {

    enum class Mode { PHOTO, VIDEO }

    var camera: Camera? = null
        private set
    private var cameraProvider: ProcessCameraProvider? = null
    private var currentLifecycleOwner: LifecycleOwner? = null
    private var currentPreviewView: PreviewView? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var srtJob: Job? = null
    private var currentPhysicalRotation = Surface.ROTATION_0
    private var currentMode: Mode = Mode.PHOTO

    val isRecordingVideo: Boolean get() = activeRecording != null

    private val orientationEventListener = object : OrientationEventListener(context) {
        override fun onOrientationChanged(orientation: Int) {
            if (orientation == ORIENTATION_UNKNOWN) return
            val rotation = when (orientation) {
                in 45..134 -> Surface.ROTATION_270
                in 135..224 -> Surface.ROTATION_180
                in 225..314 -> Surface.ROTATION_90
                else -> Surface.ROTATION_0
            }
            if (currentPhysicalRotation != rotation) {
                currentPhysicalRotation = rotation
                imageCapture?.targetRotation = rotation
            }
        }
    }

    fun setTargetRotation(rotation: Int) {
        currentPhysicalRotation = rotation
        imageCapture?.targetRotation = rotation
    }

    fun focusAndMeter(x: Float, y: Float) {
        val cam = camera ?: return
        val pv = currentPreviewView ?: return
        try {
            val factory = pv.meteringPointFactory
            val point = factory.createPoint(x, y)
            val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                .build()
            cam.cameraControl.startFocusAndMetering(action)
        } catch (e: Exception) {
            // Ignore focus metering failure on unsupported hardware
        }
    }

    fun setCaptureMode(mode: Mode) {
        if (currentMode == mode) return
        currentMode = mode
        bindMode(mode)
    }

    private fun bindMode(mode: Mode) {
        val provider = cameraProvider ?: return
        val lifecycle = currentLifecycleOwner ?: return
        val prv = preview ?: return

        provider.unbindAll()

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        if (mode == Mode.VIDEO) {
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST, FallbackStrategy.higherQualityOrLowerThan(Quality.SD)))
                .build()
            val vCap = VideoCapture.withOutput(recorder)
            videoCapture = vCap
            try {
                camera = provider.bindToLifecycle(
                    lifecycle,
                    cameraSelector,
                    prv,
                    vCap
                )
            } catch (e: Exception) {
                videoCapture = null
                camera = provider.bindToLifecycle(
                    lifecycle,
                    cameraSelector,
                    prv
                )
            }
        } else {
            // PHOTO MODE: Bind ONLY Preview and ImageCapture.
            // Crucial: Removing VideoCapture frees Camera2 HAL from strict 30 FPS video record intent,
            // enabling variable low-light FPS (5-30fps) and proper auto-exposure gain on budget sensors.
            videoCapture = null
            val imgCap = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetRotation(currentPhysicalRotation)
                .build().also { imageCapture = it }

            camera = provider.bindToLifecycle(
                lifecycle,
                cameraSelector,
                prv,
                imgCap
            )
        }
    }

    suspend fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        initialMode: Mode = Mode.PHOTO
    ): ProcessCameraProvider = suspendCoroutine { continuation ->
        currentLifecycleOwner = lifecycleOwner
        currentPreviewView = previewView
        currentMode = initialMode

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider

                preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                if (orientationEventListener.canDetectOrientation()) {
                    orientationEventListener.enable()
                }

                bindMode(initialMode)

                continuation.resume(provider)
            } catch (exc: Exception) {
                continuation.resumeWithException(exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun startVideoRecording(
        telemetryProvider: () -> TelemetryData,
        unitSystem: UnitSystem,
        onVideoSaved: (Uri) -> Unit,
        onError: (String) -> Unit
    ) {
        val vCapture = videoCapture ?: run {
            onError("Video recording not supported on this camera device")
            return
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val videoFileName = "TELEMETRY_VIDEO_${timestamp}.mp4"
        val srtFileName = "TELEMETRY_VIDEO_${timestamp}.srt"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, videoFileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/TelemetryCamera")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        val srtEntries = mutableListOf<String>()
        val startTimeMs = System.currentTimeMillis()
        var srtIndex = 1

        srtJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                val currentMs = System.currentTimeMillis() - startTimeMs
                val startFormatted = formatSrtTime(currentMs)
                delay(1000L)
                val endFormatted = formatSrtTime(System.currentTimeMillis() - startTimeMs)

                val t = telemetryProvider()
                val line1 = "HDG: ${t.formattedHeading()} | PITCH: ${t.formattedElevation()} | ROLL: ${t.formattedRoll()}"
                val line2 = "ALT: ${t.formattedAltitude(unitSystem)} | SPD: ${t.formattedSpeed(unitSystem)}"
                val line3 = t.formattedCoordinatesDecimal()

                val entry = "$srtIndex\n$startFormatted --> $endFormatted\n$line1\n$line2 | $line3\n"
                srtEntries.add(entry)
                srtIndex++
            }
        }

        val recording = vCapture.output
            .prepareRecording(context, mediaStoreOutputOptions)
            .apply {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Finalize -> {
                        srtJob?.cancel()
                        if (event.hasError()) {
                            onError("Video error: ${event.cause?.message ?: event.error}")
                        } else {
                            val savedUri = event.outputResults.outputUri
                            saveSrtFile(srtFileName, srtEntries.joinToString("\n"))
                            onVideoSaved(savedUri)
                        }
                        activeRecording = null
                    }
                }
            }

        activeRecording = recording
    }

    fun stopVideoRecording() {
        activeRecording?.stop()
        activeRecording = null
        srtJob?.cancel()
    }

    private fun formatSrtTime(ms: Long): String {
        val hours = ms / 3600000
        val minutes = (ms % 3600000) / 60000
        val seconds = (ms % 60000) / 1000
        val millis = ms % 1000
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }

    private fun saveSrtFile(fileName: String, content: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/x-subrip")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/TelemetryCamera")
                }
                val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun capturePhoto(
        telemetry: TelemetryData,
        burnOverlay: Boolean,
        settings: com.telemetry.camera.data.AppSettings = com.telemetry.camera.data.AppSettings()
    ): Uri = withContext(Dispatchers.IO) {
        val capture = imageCapture ?: throw IllegalStateException("Camera not initialized")
        capture.targetRotation = currentPhysicalRotation

        // 1. Capture to temporary cache file
        val tempFile = File.createTempFile("capture_", ".jpg", context.cacheDir)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

        suspendCoroutine<Unit> { continuation ->
            capture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        continuation.resume(Unit)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        continuation.resumeWithException(exception)
                    }
                }
            )
        }

        val exif = ExifInterface(tempFile.absolutePath)
        val exifOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

        // 2. Process image (Burn overlay or watermark badge if requested)
        val needsBurn = burnOverlay || settings.showWatermarkBadge
        val processedFile = if (needsBurn) {
            val rawBitmap = BitmapFactory.decodeFile(tempFile.absolutePath)
            val matrix = Matrix()
            when (exifOrientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            }
            val uprightBitmap = if (!matrix.isIdentity) {
                val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                rawBitmap.recycle()
                rotated
            } else {
                rawBitmap
            }

            val watermarkedBitmap = if (burnOverlay) {
                ImageOverlayProcessor.burnOverlayIntoBitmap(
                    uprightBitmap,
                    telemetry.copy(isBurned = true),
                    settings
                )
            } else {
                ImageOverlayProcessor.burnWatermarkBadgeOnly(
                    uprightBitmap,
                    settings
                )
            }

            val burnedFile = File.createTempFile("processed_", ".jpg", context.cacheDir)
            FileOutputStream(burnedFile).use { out ->
                watermarkedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            if (uprightBitmap != watermarkedBitmap) {
                watermarkedBitmap.recycle()
            }
            uprightBitmap.recycle()
            tempFile.delete()

            // Pixels are physically upright (landscape or portrait), orientation is NORMAL
            ExifTelemetryHelper.writeTelemetryToExif(
                burnedFile,
                telemetry.copy(isBurned = burnOverlay),
                orientation = ExifInterface.ORIENTATION_NORMAL,
                unitSystem = settings.unitSystem
            )
            burnedFile
        } else {
            // Clean photo: preserve CameraX's EXIF orientation tag
            ExifTelemetryHelper.writeTelemetryToExif(
                tempFile,
                telemetry.copy(isBurned = false),
                orientation = exifOrientation,
                unitSystem = settings.unitSystem
            )
            tempFile
        }

        // 3. Save to MediaStore (Pictures/TelemetryCamera)
        val savedUri = saveToMediaStore(processedFile, telemetry.copy(isBurned = burnOverlay), burnOverlay)
        processedFile.delete()

        savedUri
    }

    private fun saveToMediaStore(sourceFile: File, telemetry: TelemetryData, isBurned: Boolean): Uri {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(telemetry.timestamp))
        val fileName = if (isBurned) "TELEMETRY_BURNED_${timeStamp}.jpg" else "TELEMETRY_${timeStamp}.jpg"

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
            put(MediaStore.Images.Media.DESCRIPTION, telemetry.toJson())
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Failed to create MediaStore record")

        resolver.openOutputStream(uri)?.use { out ->
            sourceFile.inputStream().use { input ->
                input.copyTo(out)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }

        return uri
    }
}
