package com.japonbaligi.mp3downloader

object YouTubeUrlHelper {
    private const val YOUTUBE_DOMAIN = "youtube.com"
    private const val WATCH_PATTERN = "/watch?v="
    private const val SHORTS_PATTERN = "/shorts/"

    /**
     * Checks if the URL is a valid YouTube video page
     * Valid formats:
     * - youtube.com/watch?v=VIDEO_ID
     * - youtube.com/shorts/VIDEO_ID
     */
    fun isVideoPage(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        
        val normalizedUrl = url.lowercase()
        if (!normalizedUrl.contains(YOUTUBE_DOMAIN)) return false
        
        return normalizedUrl.contains(WATCH_PATTERN) || normalizedUrl.contains(SHORTS_PATTERN)
    }

    /**
     * Extracts video ID from YouTube URL
     * Supports:
     * - youtube.com/watch?v=VIDEO_ID
     * - youtube.com/shorts/VIDEO_ID
     * Returns null if no valid video ID is found
     */
    fun extractVideoId(url: String?): String? {
        if (url.isNullOrBlank()) return null

        // Try to extract from /watch?v= format
        val watchIndex = url.indexOf(WATCH_PATTERN)
        if (watchIndex != -1) {
            val startIndex = watchIndex + WATCH_PATTERN.length
            val endIndex = url.indexOf('&', startIndex).let { if (it == -1) url.length else it }
            val videoId = url.substring(startIndex, endIndex)
            if (videoId.isNotBlank()) return videoId
        }

        // Try to extract from /shorts/ format
        val shortsIndex = url.indexOf(SHORTS_PATTERN)
        if (shortsIndex != -1) {
            val startIndex = shortsIndex + SHORTS_PATTERN.length
            val endIndex = url.indexOf('?', startIndex).let { if (it == -1) url.indexOf('/', startIndex).let { idx -> if (idx == -1) url.length else idx } else it }
            val videoId = url.substring(startIndex, endIndex)
            if (videoId.isNotBlank()) return videoId
        }

        return null
    }

    /**
     * Normalizes YouTube URL to watch?v=VIDEO_ID format
     * Converts shorts URLs to watch format
     * Returns null if URL is not a valid video page
     */
    fun normalizeVideoUrl(url: String?): String? {
        val videoId = extractVideoId(url) ?: return null
        return "https://www.youtube.com/watch?v=$videoId"
    }
}

