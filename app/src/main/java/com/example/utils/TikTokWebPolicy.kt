package com.example.utils

import java.net.URI

object TikTokWebPolicy {
    private val uploadPathPrefixes = listOf(
        "/tiktokstudio/upload",
        "/upload",
        "/creator-center"
    )

    fun isTrustedTikTokOrigin(url: String?): Boolean {
        val uri = parseHttpsUri(url) ?: return false
        val host = uri.host?.lowercase() ?: return false
        return host == "tiktok.com" || host.endsWith(".tiktok.com")
    }

    fun isAllowedUploadUrl(url: String?): Boolean {
        val uri = parseHttpsUri(url) ?: return false
        val host = uri.host?.lowercase() ?: return false
        if (host != "tiktok.com" && !host.endsWith(".tiktok.com")) return false

        val path = uri.path.orEmpty().lowercase().trimEnd('/')
        return uploadPathPrefixes.any { prefix ->
            path == prefix || path.startsWith("$prefix/")
        }
    }

    fun acceptsVideo(acceptTypes: Array<String>?): Boolean {
        return acceptTypes
            ?.asSequence()
            ?.flatMap { it.split(',').asSequence() }
            ?.map { it.trim().lowercase() }
            ?.filter { it.isNotEmpty() }
            ?.any { type ->
                type == "video/*" ||
                    type.startsWith("video/") ||
                    type == ".mp4" ||
                    type.endsWith("/mp4")
            } == true
    }

    private fun parseHttpsUri(url: String?): URI? {
        if (url.isNullOrBlank()) return null
        return runCatching { URI(url) }
            .getOrNull()
            ?.takeIf { it.scheme.equals("https", ignoreCase = true) }
    }
}
