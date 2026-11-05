package com.telemetry.camera.data

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class GalleryPhoto(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateTaken: Long,
    val isVideo: Boolean = displayName.endsWith(".mp4", ignoreCase = true),
    val isBurned: Boolean = displayName.contains("BURNED"),
    val telemetry: TelemetryData? = null
)

sealed class DeleteResult {
    object Success : DeleteResult()
    data class RequiresPermission(val intentSender: IntentSender) : DeleteResult()
    object Failed : DeleteResult()
}

class GalleryRepository(private val context: Context) {

    suspend fun getTelemetryPhotos(): List<GalleryPhoto> = withContext(Dispatchers.IO) {
        val items = mutableListOf<GalleryPhoto>()

        // 1. Query Images
        val imageProjection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN
        )

        val imageSelection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
        } else {
            "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
        }

        val imageArgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf("Pictures/TelemetryCamera%", "TELEMETRY_%")
        } else {
            arrayOf("TELEMETRY_%")
        }

        val sortOrder = "${MediaStore.MediaColumns.DATE_TAKEN} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                imageProjection,
                imageSelection,
                imageArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "Photo"
                    val dateTaken = cursor.getLong(dateColumn)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    items.add(
                        GalleryPhoto(
                            id = id,
                            uri = contentUri,
                            displayName = name,
                            dateTaken = dateTaken,
                            isVideo = false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Query Videos
        val videoProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_TAKEN
        )

        val videoSelection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?"
        } else {
            "${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?"
        }

        val videoArgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf("Movies/TelemetryCamera%", "TELEMETRY_VIDEO_%")
        } else {
            arrayOf("TELEMETRY_VIDEO_%")
        }

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                videoSelection,
                videoArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_TAKEN)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "Video"
                    val dateTaken = cursor.getLong(dateColumn)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    items.add(
                        GalleryPhoto(
                            id = id + 1_000_000_000L,
                            uri = contentUri,
                            displayName = name,
                            dateTaken = dateTaken,
                            isVideo = true
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Fallback: Scan direct directories for images & movies if needed
        val picturesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "TelemetryCamera")
        val moviesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "TelemetryCamera")

        val scannedFiles = mutableListOf<File>()
        if (picturesDir.exists() && picturesDir.isDirectory) {
            picturesDir.listFiles { f ->
                f.isFile && f.name.startsWith("TELEMETRY_") && (f.name.endsWith(".jpg", true) || f.name.endsWith(".jpeg", true))
            }?.let { scannedFiles.addAll(it) }
        }
        if (moviesDir.exists() && moviesDir.isDirectory) {
            moviesDir.listFiles { f ->
                f.isFile && f.name.startsWith("TELEMETRY_VIDEO_") && f.name.endsWith(".mp4", true)
            }?.let { scannedFiles.addAll(it) }
        }

        for ((index, file) in scannedFiles.withIndex()) {
            val fileUri = Uri.fromFile(file)
            if (items.none { it.displayName == file.name }) {
                items.add(
                    GalleryPhoto(
                        id = -100L - index,
                        uri = fileUri,
                        displayName = file.name,
                        dateTaken = file.lastModified(),
                        isVideo = file.name.endsWith(".mp4", ignoreCase = true)
                    )
                )
            }
        }

        items.sortedByDescending { it.dateTaken }
    }

    private fun deleteCompanionFiles(uri: Uri) {
        try {
            val projection = arrayOf(MediaStore.MediaColumns.DATA)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val path = cursor.getString(0)
                    if (path != null) {
                        val mainFile = File(path)
                        val baseName = mainFile.nameWithoutExtension
                        val parent = mainFile.parentFile
                        if (parent != null && parent.exists()) {
                            val jsonFile = File(parent, "$baseName.json")
                            if (jsonFile.exists()) jsonFile.delete()

                            val srtFile = File(parent, "$baseName.srt")
                            if (srtFile.exists()) srtFile.delete()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignored: best-effort cleanup of companion files
        }
    }

    private fun tryDeleteUnderlyingFile(uri: Uri): Boolean {
        return try {
            val projection = arrayOf(MediaStore.MediaColumns.DATA)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val path = cursor.getString(0)
                    if (path != null) {
                        val file = File(path)
                        if (file.exists() && file.delete()) {
                            return true
                        }
                    }
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deletePhoto(uri: Uri): DeleteResult = withContext(Dispatchers.IO) {
        deleteCompanionFiles(uri)

        // 1. If it's a file:// URI, delete directly
        if (uri.scheme == "file") {
            try {
                val file = uri.path?.let { File(it) }
                if (file != null && file.exists()) {
                    val ok = file.delete()
                    if (ok) return@withContext DeleteResult.Success
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Try standard ContentResolver delete (succeeds without OS prompt for app-owned files)
        try {
            val rows = context.contentResolver.delete(uri, null, null)
            if (rows > 0) return@withContext DeleteResult.Success
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val intentSender = MediaStore.createDeleteRequest(context.contentResolver, listOf(uri)).intentSender
                    return@withContext DeleteResult.RequiresPermission(intentSender)
                } catch (e2: Exception) {
                    e2.printStackTrace()
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                return@withContext DeleteResult.RequiresPermission(e.userAction.actionIntent.intentSender)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Fallback: query underlying file path if ContentResolver deletion didn't throw permission prompt
        if (tryDeleteUnderlyingFile(uri)) {
            return@withContext DeleteResult.Success
        }

        DeleteResult.Failed
    }

    suspend fun deletePhotos(uris: List<Uri>): DeleteResult = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext DeleteResult.Success
        if (uris.size == 1) return@withContext deletePhoto(uris.first())

        val unhandledUris = mutableListOf<Uri>()

        for (uri in uris) {
            deleteCompanionFiles(uri)
            if (uri.scheme == "file") {
                try {
                    val file = uri.path?.let { File(it) }
                    if (file != null && file.exists()) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                var deleted = false
                try {
                    val rows = context.contentResolver.delete(uri, null, null)
                    if (rows > 0) {
                        deleted = true
                    }
                } catch (e: SecurityException) {
                    // Elevated permission required for this URI
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                if (!deleted) {
                    if (tryDeleteUnderlyingFile(uri)) {
                        deleted = true
                    }
                }

                if (!deleted) {
                    unhandledUris.add(uri)
                }
            }
        }

        if (unhandledUris.isEmpty()) {
            return@withContext DeleteResult.Success
        }

        // Only request system permission for files that couldn't be deleted directly
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intentSender = MediaStore.createDeleteRequest(context.contentResolver, unhandledUris).intentSender
                return@withContext DeleteResult.RequiresPermission(intentSender)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            for (uri in unhandledUris) {
                try {
                    context.contentResolver.delete(uri, null, null)
                } catch (e: RecoverableSecurityException) {
                    return@withContext DeleteResult.RequiresPermission(e.userAction.actionIntent.intentSender)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        DeleteResult.Success
    }
}
