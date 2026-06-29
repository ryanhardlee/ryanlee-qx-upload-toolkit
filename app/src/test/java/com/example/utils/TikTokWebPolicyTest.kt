package com.example.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TikTokWebPolicyTest {
    @Test
    fun allowsOnlyHttpsTikTokOrigins() {
        assertTrue(TikTokWebPolicy.isTrustedTikTokOrigin("https://www.tiktok.com/login"))
        assertTrue(TikTokWebPolicy.isTrustedTikTokOrigin("https://accounts.tiktok.com/"))
        assertFalse(TikTokWebPolicy.isTrustedTikTokOrigin("http://www.tiktok.com/upload"))
        assertFalse(TikTokWebPolicy.isTrustedTikTokOrigin("https://tiktok.com.example.org/upload"))
    }

    @Test
    fun allowsOnlyTikTokUploadPathsForPrivilegedBehavior() {
        assertTrue(TikTokWebPolicy.isAllowedUploadUrl("https://www.tiktok.com/tiktokstudio/upload?from=webapp"))
        assertTrue(TikTokWebPolicy.isAllowedUploadUrl("https://www.tiktok.com/upload"))
        assertTrue(TikTokWebPolicy.isAllowedUploadUrl("https://www.tiktok.com/creator-center/content/upload"))
        assertFalse(TikTokWebPolicy.isAllowedUploadUrl("https://www.tiktok.com/login"))
        assertFalse(TikTokWebPolicy.isAllowedUploadUrl("https://example.com/tiktokstudio/upload"))
    }

    @Test
    fun detectsExplicitVideoFileRequests() {
        assertTrue(TikTokWebPolicy.acceptsVideo(arrayOf("video/*")))
        assertTrue(TikTokWebPolicy.acceptsVideo(arrayOf("video/mp4")))
        assertTrue(TikTokWebPolicy.acceptsVideo(arrayOf(".mp4")))
        assertTrue(TikTokWebPolicy.acceptsVideo(arrayOf("image/*, video/mp4")))
        assertFalse(TikTokWebPolicy.acceptsVideo(emptyArray()))
        assertFalse(TikTokWebPolicy.acceptsVideo(arrayOf("image/*")))
    }
}
