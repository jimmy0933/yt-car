package com.jimmy.ytcar

import android.net.Uri

object YouTubeUrls {
    private val ID = Regex("^[A-Za-z0-9_-]{11}$")
    private val URL_IN_TEXT = Regex("https?://\\S+")

    /** 從網址取出影片 ID，支援 watch?v=、/shorts/、/live/、youtu.be */
    fun videoId(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val segs = uri.pathSegments
        val id = when {
            host == "youtu.be" -> segs.firstOrNull()
            host.endsWith("youtube.com") -> when {
                uri.path == "/watch" -> uri.getQueryParameter("v")
                segs.size >= 2 && segs[0] in setOf("shorts", "live", "embed") -> segs[1]
                else -> null
            }
            else -> null
        }
        return id?.takeIf { ID.matches(it) }
    }

    /** 分享文字中可能夾雜標題，找出第一個 YouTube 網址 */
    fun videoIdFromSharedText(text: String?): String? =
        text?.let { t -> URL_IN_TEXT.findAll(t).firstNotNullOfOrNull { videoId(it.value) } }
}
