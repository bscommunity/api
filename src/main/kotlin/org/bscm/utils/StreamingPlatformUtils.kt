package org.bscm.utils

import org.bscm.models.StreamingLink
import org.bscm.models.enums.StreamingPlatform

/**
 * Utility object for handling streaming platform detection and prioritization
 */
object StreamingPlatformUtils {

    private data class PlatformGroup(
        val keywords: List<String>,
        val group: String,
        val platform: StreamingPlatform
    )

    /**
     * Platform group definitions for prioritization
     */
    private val PLATFORM_GROUPS = listOf(
        PlatformGroup(listOf("youtu"), "youtube", StreamingPlatform.YOUTUBE_MUSIC),
        PlatformGroup(listOf("apple", "itunes"), "apple", StreamingPlatform.APPLE_MUSIC),
        PlatformGroup(listOf("amazon"), "amazon", StreamingPlatform.AMAZON_MUSIC),
        PlatformGroup(listOf("spotify"), "spotify", StreamingPlatform.SPOTIFY),
        PlatformGroup(listOf("deezer"), "deezer", StreamingPlatform.DEEZER),
        PlatformGroup(listOf("tidal"), "tidal", StreamingPlatform.TIDAL),
        PlatformGroup(listOf("soundcloud"), "soundcloud", StreamingPlatform.SOUNDCLOUD),
        PlatformGroup(listOf("last"), "lastfm", StreamingPlatform.LAST_FM),
    )

    /**
     * Detects the platform from a key or URL string
     */
    private fun detectPlatform(keyOrUrl: String): StreamingPlatform? {
        val normalized = keyOrUrl.lowercase()

        for (group in PLATFORM_GROUPS) {
            if (group.keywords.any { keyword -> normalized.contains(keyword) }) {
                return group.platform
            }
        }

        return null
    }

    /**
     * Gets the platform group identifier for prioritization
     */
    private fun getPlatformGroup(keyOrUrl: String): String? {
        val normalized = keyOrUrl.lowercase()

        for (group in PLATFORM_GROUPS) {
            if (group.keywords.any { keyword -> normalized.contains(keyword) }) {
                return group.group
            }
        }

        return null
    }

    /**
     * Checks if a key or URL represents a "music" version of a platform
     */
    private fun isMusicVersion(keyOrUrl: String): Boolean {
        return keyOrUrl.lowercase().contains("music")
    }

    /**
     * Legacy method for Odesli keys
     */
    fun fromKey(key: String): StreamingPlatform {
        return detectPlatform(key) ?: StreamingPlatform.SPOTIFY
    }

    /**
     * Legacy method for URL-based detection
     */
    fun fromUrl(url: String): StreamingPlatform {
        return detectPlatform(url) ?: StreamingPlatform.SPOTIFY
    }

    /**
     * Processes and prioritizes streaming links
     * Prioritizes "music" versions within the same platform group
     *
     * @param links List of StreamingLink objects to process
     * @param useKeyForDetection If true, use platform from StreamingLink (Odesli mode); if false, detect from URL
     * @return Deduplicated and prioritized list of streaming links
     */
    fun processLinksWithPrioritization(
        links: List<StreamingLink>,
        useKeyForDetection: Boolean = false
    ): List<StreamingLink> {
        val linkMap = mutableMapOf<String, LinkData>()

        // Process all links
        for (link in links) {
            // Use platform for Odesli (already detected), URL for others
            val searchString = if (useKeyForDetection) link.platform.name else link.url
            val platform = detectPlatform(searchString)
            val groupKey = getPlatformGroup(searchString)

            if (platform == null || link.url.isBlank() || groupKey == null) {
                println("Skipping unknown platform or missing data: ${link.platform} - ${link.url}")
                continue
            }

            val isMusicVer = isMusicVersion(searchString)
            val existing = linkMap[groupKey]

            if (existing == null) {
                // First link for this group
                linkMap[groupKey] = LinkData(link.url, platform, isMusicVer)
            } else {
                // Replace if current is music version and existing is not
                if (isMusicVer && !existing.isMusicVersion) {
                    linkMap[groupKey] = LinkData(link.url, platform, isMusicVer)
                }
            }
        }

        // Convert to final format and log
        return linkMap.values.map { data ->
            // println("Found link for platform: ${data.platform}, URL: ${data.url}")
            StreamingLink(data.platform, data.url)
        }
    }

    private data class LinkData(
        val url: String,
        val platform: StreamingPlatform,
        val isMusicVersion: Boolean
    )
}