package com.example.utils

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object FileProviderShareService {
    private const val TAG = "FileProviderShare"

    fun shareVideo(context: Context, videoFile: File): Boolean {
        try {
            if (!videoFile.exists()) {
                Toast.makeText(context, "File does not exist!", Toast.LENGTH_SHORT).show()
                return false
            }

            val authority = "${context.packageName}.fileprovider"
            Log.d(TAG, "Sharing file using FileProvider with authority: $authority")
            
            val contentUri = FileProvider.getUriForFile(context, authority, videoFile)
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // ClipData is required for grant permission to stick on some older/custom Android distributions
                clipData = ClipData.newRawUri("Video", contentUri)
            }
            
            val chooserIntent = Intent.createChooser(intent, "Share Prepared Video").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooserIntent)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error in FileProviderShareService", e)
            Toast.makeText(context, "Error sharing file: ${e.message}", Toast.LENGTH_LONG).show()
            return false
        }
    }
}
