package com.example.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

object CaptionService {
    const val DEFAULT_CAPTION = "Upload method by @ryanhardlee #RyanLeeQX"

    fun copyToClipboard(context: Context, text: String): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("RyanLee QX Caption", text)
            clipboard.setPrimaryClip(clip)
            true
        } catch (e: Exception) {
            false
        }
    }
}
