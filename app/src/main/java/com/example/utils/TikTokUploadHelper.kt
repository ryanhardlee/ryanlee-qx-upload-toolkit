package com.example.utils

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Browser
import android.util.Log
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent

object TikTokUploadHelper {
    private const val TAG = "TikTokUploadHelper"
    const val TIKTOK_STUDIO_UPLOAD_URL = "https://www.tiktok.com/tiktokstudio/upload?from=webapp"
    const val REQUIRED_CAPTION = "Upload method by @ryanhardlee"

    /**
     * Opens TikTok Studio upload page using Chrome Custom Tabs after copying the required caption.
     * Falls back to standard ACTION_VIEW if Chrome Custom Tabs cannot launch.
     *
     * @return Pair<Boolean, String> indicating (success, status message)
     */
    fun openChromeCustomTabUpload(context: Context): Pair<Boolean, String> {
        // 1. Copy required caption to clipboard
        val captionCopied = CaptionService.copyToClipboard(context, REQUIRED_CAPTION)
        val captionStatus = if (captionCopied) "Caption copied" else "Caption copy failed"

        val uri = Uri.parse(TIKTOK_STUDIO_UPLOAD_URL)

        return try {
            // 2. Configure RyanLee QX cyber-dark branding for toolbar
            val darkParams = CustomTabColorSchemeParams.Builder()
                .setToolbarColor(Color.parseColor("#0A0E1A")) // CyberNavy / Dark base
                .setNavigationBarColor(Color.parseColor("#05070D"))
                .build()

            val customTabsIntent = CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(darkParams)
                .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
                .setShowTitle(true)
                .setShareState(CustomTabsIntent.SHARE_STATE_ON)
                .build()

            customTabsIntent.intent.setPackage("com.android.chrome")
            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            // Force desktop mode simulation if supported by target browser
            val headers = Bundle().apply {
                putString("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
            }
            customTabsIntent.intent.putExtra(Browser.EXTRA_HEADERS, headers)

            customTabsIntent.launchUrl(context, uri)
            Log.d(TAG, "Launched Chrome Custom Tab successfully for $TIKTOK_STUDIO_UPLOAD_URL")
            Pair(true, "Launched Chrome Custom Tab ($captionStatus)")
        } catch (e: Exception) {
            Log.w(TAG, "Chrome Custom Tab launch failed, attempting fallback ACTION_VIEW", e)
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                Log.d(TAG, "Launched fallback ACTION_VIEW successfully")
                Pair(true, "Launched standard browser fallback ($captionStatus)")
            } catch (fe: Exception) {
                Log.e(TAG, "Both Chrome Custom Tab and fallback browser launch failed", fe)
                Pair(false, "Failed to open browser: ${fe.message}")
            }
        }
    }

    private val EXTENSION_BROWSERS = listOf(
        Pair("Lemur Browser", "com.lemurbrowser.exts"),
        Pair("Kiwi Browser", "com.kiwibrowser.browser"),
        Pair("Yandex Browser", "com.yandex.browser")
    )

    /**
     * Attempts to open TikTok Studio upload URL in extension-capable Android browsers
     * (Lemur, Kiwi, Yandex). Falls back to normal browser chooser if none are installed.
     */
    fun openQxExtensionBrowser(context: Context): Pair<Boolean, String> {
        val uri = Uri.parse(TIKTOK_STUDIO_UPLOAD_URL)
        val pm = context.packageManager

        for ((name, pkg) in EXTENSION_BROWSERS) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage(pkg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (intent.resolveActivity(pm) != null) {
                    context.startActivity(intent)
                    Log.d(TAG, "Launched $name ($pkg) for $TIKTOK_STUDIO_UPLOAD_URL")
                    return Pair(true, "Opened in $name")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to launch $name ($pkg)", e)
            }
        }

        // Fallback: normal browser chooser
        return try {
            val chooserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(chooserIntent, "Open TikTok Studio Upload").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            Log.d(TAG, "Launched standard browser chooser fallback")
            Pair(true, "Opened in Browser chooser")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch browser chooser", e)
            Pair(false, "No web browser available: ${e.message}")
        }
    }
}
