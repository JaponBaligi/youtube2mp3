package com.japonbaligi.mp3downloader

import java.util.regex.Pattern

object YouTubeUrlHelper {
    private const val YOUTUBE_DOMAIN = "youtube.com"
    private const val YOUTUBE_SHORT_DOMAIN = "youtu.be"
    private const val WATCH_PATTERN = "/watch?v="
    private const val SHORTS_PATTERN = "/shorts/"

    // Pattern to match video ID (11 characters, alphanumeric, hyphens, underscores)
    private val VIDEO_ID_PATTERN = Pattern.compile("[a-zA-Z0-9_-]{11}")

    /**
     * Checks if the URL is a valid YouTube video page
     * Valid formats:
     * - youtube.com/watch?v=VIDEO_ID
     * - youtube.com/shorts/VIDEO_ID
     * - youtu.be/VIDEO_ID
     * - m.youtube.com/watch?v=VIDEO_ID
     * - music.youtube.com/watch?v=VIDEO_ID
     */
    fun isVideoPage(url: String?): Boolean {
        if (url.isNullOrBlank()) return false

        val normalizedUrl = url.lowercase().trim()
        if (!normalizedUrl.contains(YOUTUBE_DOMAIN) && !normalizedUrl.contains(YOUTUBE_SHORT_DOMAIN)) {
            return false
        }

        return normalizedUrl.contains(WATCH_PATTERN) ||
                normalizedUrl.contains(SHORTS_PATTERN) ||
                normalizedUrl.contains("$YOUTUBE_SHORT_DOMAIN/")
    }

    /**
     * Extracts video ID from YouTube URL
     * Supports:
     * - youtube.com/watch?v=VIDEO_ID
     * - youtube.com/shorts/VIDEO_ID
     * - youtu.be/VIDEO_ID
     * - m.youtube.com/watch?v=VIDEO_ID
     * - music.youtube.com/watch?v=VIDEO_ID
     * Returns null if no valid video ID is found
     */
    fun extractVideoId(url: String?): String? {
        if (url.isNullOrBlank()) return null

        val cleanUrl = url.trim()
        val lowerUrl = cleanUrl.lowercase()

        // Try to extract from /watch?v= format
        val watchIndex = lowerUrl.indexOf(WATCH_PATTERN)
        if (watchIndex != -1) {
            val startIndex = watchIndex + WATCH_PATTERN.length
            val endIndex = findVideoIdEnd(cleanUrl, startIndex)
            val videoId = cleanUrl.substring(startIndex, endIndex)
            if (isValidVideoId(videoId)) return videoId
        }

        // Try to extract from /shorts/ format
        val shortsIndex = lowerUrl.indexOf(SHORTS_PATTERN)
        if (shortsIndex != -1) {
            val startIndex = shortsIndex + SHORTS_PATTERN.length
            val endIndex = findVideoIdEnd(cleanUrl, startIndex)
            val videoId = cleanUrl.substring(startIndex, endIndex)
            if (isValidVideoId(videoId)) return videoId
        }

        // Try to extract from youtu.be/VIDEO_ID format
        val shortDomainIndex = lowerUrl.indexOf("$YOUTUBE_SHORT_DOMAIN/")
        if (shortDomainIndex != -1) {
            val startIndex = shortDomainIndex + YOUTUBE_SHORT_DOMAIN.length + 1
            val endIndex = findVideoIdEnd(cleanUrl, startIndex)
            val videoId = cleanUrl.substring(startIndex, endIndex)
            if (isValidVideoId(videoId)) return videoId
        }

        return null
    }

    /**
     * Finds the end index of a video ID in a URL.
     * Stops at '&', '?', '/', '#' or end of string.
     */
    private fun findVideoIdEnd(url: String, startIndex: Int): Int {
        var endIndex = url.length
        for (i in startIndex until url.length) {
            val char = url[i]
            if (char == '&' || char == '?' || char == '/' || char == '#') {
                endIndex = i
                break
            }
        }
        return endIndex
    }

    /**
     * Validates that a string is a valid YouTube video ID.
     * Video IDs are typically 11 characters, alphanumeric with hyphens and underscores.
     */
    private fun isValidVideoId(videoId: String): Boolean {
        return videoId.isNotBlank() && VIDEO_ID_PATTERN.matcher(videoId).matches()
    }

    /**
     * Normalizes YouTube URL to watch?v=VIDEO_ID format
     * Converts shorts URLs and youtu.be URLs to watch format
     * Returns null if URL is not a valid video page
     */
    fun normalizeVideoUrl(url: String?): String? {
        val videoId = extractVideoId(url) ?: return null
        return "https://www.youtube.com/watch?v=$videoId"
    }
}
