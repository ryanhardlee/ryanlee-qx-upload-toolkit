package com.example.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MediaStoreExportService {
    private const val TAG = "MediaStoreExport"

    data class ExportResult(
        val success: Boolean,
        val savedUri: Uri? = null,
        val filename: String = "",
        val folder: String = "Movies/RyanLee QX",
        val fileSize: Long = 0L,
        val errorType: String? = null,
        val errorMessage: String? = null,
        val stackTrace: String? = null,
        val sourcePath: String = "",
        val destFolder: String = "Movies/RyanLee QX",
        
        // Verification details
        val sourceExists: Boolean = false,
        val sourceSize: Long = 0L,
        val savedDisplayName: String? = null,
        val savedUriString: String? = null,
        val savedSizeFromQuery: Long? = null,
        val savedRelativePath: String? = null,
        val savedMimeType: String? = null,
        val savedIsPending: Int? = null,
        val verificationResult: String = "FAIL" // "PASS" or "FAIL"
    )

    fun saveVideoToGallery(context: Context, cacheFile: File, prefix: String, modeSuffix: String): ExportResult {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "${prefix}_${timestamp}_${modeSuffix.replace(" ", "_")}.mp4"
        val folderPath = Environment.DIRECTORY_MOVIES + "/RyanLee QX"
        val size = cacheFile.length()
        val sourceExists = cacheFile.exists()

        if (!sourceExists) {
            return ExportResult(
                success = false,
                errorMessage = "Source cache file does not exist at: ${cacheFile.absolutePath}",
                sourcePath = cacheFile.absolutePath,
                destFolder = folderPath,
                fileSize = size,
                sourceExists = false,
                sourceSize = 0L,
                filename = filename,
                verificationResult = "FAIL"
            )
        }

        try {
            val contentResolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, folderPath)
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            Log.d(TAG, "Inserting video record to MediaStore collection: $collection")
            val itemUri = contentResolver.insert(collection, contentValues)
                ?: throw IllegalStateException("Failed to insert media metadata into MediaStore")

            try {
                Log.d(TAG, "Opening output stream for: $itemUri")
                contentResolver.openOutputStream(itemUri).use { outputStream ->
                    if (outputStream == null) throw IllegalStateException("Failed to open output stream for MediaStore URI")
                    cacheFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    outputStream.flush()
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                    val rowsUpdated = contentResolver.update(itemUri, contentValues, null, null)
                    Log.d(TAG, "Updated IS_PENDING=0 for $itemUri. Rows updated: $rowsUpdated")
                }

                // Immediately verify the saved file by querying ContentResolver using the saved content URI
                var savedDisplayName: String? = null
                val savedUriString = itemUri.toString()
                var savedSizeFromQuery: Long? = null
                var savedRelativePath: String? = null
                var savedMimeType: String? = null
                var savedIsPending: Int? = null
                var verificationResult = "FAIL"

                try {
                    val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        arrayOf(
                            MediaStore.Video.Media.DISPLAY_NAME,
                            MediaStore.Video.Media.SIZE,
                            MediaStore.Video.Media.RELATIVE_PATH,
                            MediaStore.Video.Media.MIME_TYPE,
                            MediaStore.Video.Media.IS_PENDING
                        )
                    } else {
                        arrayOf(
                            MediaStore.Video.Media.DISPLAY_NAME,
                            MediaStore.Video.Media.SIZE,
                            MediaStore.Video.Media.MIME_TYPE
                        )
                    }

                    contentResolver.query(itemUri, projection, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIdx = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                            val sizeIdx = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                            val mimeIdx = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
                            
                            if (nameIdx != -1) savedDisplayName = cursor.getString(nameIdx)
                            if (sizeIdx != -1) savedSizeFromQuery = cursor.getLong(sizeIdx)
                            if (mimeIdx != -1) savedMimeType = cursor.getString(mimeIdx)

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val pathIdx = cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
                                val pendingIdx = cursor.getColumnIndex(MediaStore.Video.Media.IS_PENDING)
                                if (pathIdx != -1) savedRelativePath = cursor.getString(pathIdx)
                                if (pendingIdx != -1) savedIsPending = cursor.getInt(pendingIdx)
                            }
                        }
                    }

                    val sizeMatches = savedSizeFromQuery != null && savedSizeFromQuery!! > 0L
                    val pendingOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) savedIsPending == 0 else true
                    if (sizeMatches && pendingOk) {
                        verificationResult = "PASS"
                    }
                    Log.d(TAG, "MediaStore save verification query status: $verificationResult. Size: $savedSizeFromQuery, Pending: $savedIsPending")
                } catch (queryEx: Exception) {
                    Log.e(TAG, "Verification query failed", queryEx)
                }

                return ExportResult(
                    success = (verificationResult == "PASS"),
                    savedUri = itemUri,
                    filename = filename,
                    folder = folderPath,
                    fileSize = size,
                    sourcePath = cacheFile.absolutePath,
                    destFolder = folderPath,
                    sourceExists = sourceExists,
                    sourceSize = size,
                    savedDisplayName = savedDisplayName ?: filename,
                    savedUriString = savedUriString,
                    savedSizeFromQuery = savedSizeFromQuery,
                    savedRelativePath = savedRelativePath ?: folderPath,
                    savedMimeType = savedMimeType ?: "video/mp4",
                    savedIsPending = savedIsPending,
                    verificationResult = verificationResult
                )
            } catch (e: Exception) {
                try {
                    contentResolver.delete(itemUri, null, null)
                } catch (delEx: Exception) {
                    Log.e(TAG, "Failed to delete failed insert", delEx)
                }
                throw e
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save video to gallery", e)
            val sw = java.io.StringWriter()
            e.printStackTrace(java.io.PrintWriter(sw))
            val stackSummary = sw.toString().lines().take(5).joinToString("\n")

            return ExportResult(
                success = false,
                errorType = e.javaClass.name,
                errorMessage = e.message ?: "Unknown MediaStore IO error",
                stackTrace = stackSummary,
                sourcePath = cacheFile.absolutePath,
                destFolder = folderPath,
                fileSize = size,
                sourceExists = sourceExists,
                sourceSize = size,
                filename = filename,
                verificationResult = "FAIL"
            )
        }
    }
}
