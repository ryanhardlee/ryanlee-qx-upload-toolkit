package com.example.utils

import android.content.Context
import android.net.Uri
import com.example.native.PatchEngine

object VideoInspectorService {
    fun inspectVideo(context: Context, uri: Uri): PatchEngine.VideoMetadata {
        return PatchEngine.inspectVideo(context, uri)
    }
}
