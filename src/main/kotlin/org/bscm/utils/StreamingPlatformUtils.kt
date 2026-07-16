package org.bscm.utils

import io.ktor.util.logging.*
import org.bscm.models.StreamingRef
import org.bscm.models.enums.StreamingPlatform
import java.net.URI
import java.net.URISyntaxException

private val log = KtorSimpleLogger("StreamingPlatformUtils")

/**
 * Utility object for handling streaming platform detection and prioritization
 */
object StreamingPlatformUtils {

    private data class PlatformGroup(
        val keywords: List<String>,
        val group: String,
        val platform: StreamingPlatform,
        val baseDomain: String
    )

    /**
     * Platform group definitions for prioritization
     */
    private val PLATFORM_GROUPS = listOf(
        PlatformGroup(listOf("youtu"), "youtube", StreamingPlatform.YOUTUBE_MUSIC, "music.youtube.com/"),
        PlatformGroup(listOf("apple", "itunes"), "apple", StreamingPlatform.APPLE_MUSIC, "music.apple.com/"),
        PlatformGroup(listOf("amazon"), "amazon", StreamingPlatform.AMAZON_MUSIC, "music.amazon.com/"),
        PlatformGroup(listOf("spotify"), "spotify", StreamingPlatform.SPOTIFY, "open.spotify.com/"),
        PlatformGroup(listOf("deezer"), "deezer", StreamingPlatform.DEEZER, "www.deezer.com/"),
        PlatformGroup(listOf("tidal"), "tidal", StreamingPlatform.TIDAL, "listen.tidal.com/"),
        PlatformGroup(listOf("soundcloud"), "soundcloud", StreamingPlatform.SOUNDCLOUD, "soundcloud.com/"),
        PlatformGroup(listOf("last"), "lastfm", StreamingPlatform.LAST_FM, "www.last.fm/"),
        PlatformGroup(listOf("pandora"), "pandora", StreamingPlatform.PANDORA, "www.pandora.com/"),
        PlatformGroup(listOf("napster"), "napster", StreamingPlatform.NAPSTER, "www.napster.com/"),
        PlatformGroup(listOf("qobuz"), "qobuz", StreamingPlatform.QOBUZ, "www.qobuz.com/"),
        PlatformGroup(listOf("yandex"), "yandex", StreamingPlatform.YANDEX_MUSIC, "music.yandex.ru/"),
        PlatformGroup(listOf("boomplay"), "boomplay", StreamingPlatform.BOOMPLAY, "boomplay.com/"),
        PlatformGroup(listOf("anghami"), "anghami", StreamingPlatform.ANGHAMI, "anghami.com/"),
        PlatformGroup(listOf("audiomack"), "audiomack", StreamingPlatform.AUDIOMACK, "www.audiomack.com/"),
        PlatformGroup(listOf("shazam"), "shazam", StreamingPlatform.SHAZAM, "www.shazam.com/"),
        PlatformGroup(listOf("jiosaavn"), "jiosaavn", StreamingPlatform.JIOSAAVN, "www.jiosaavn.com/"),
    )

    /**
     * Map platform to base domain for efficient serialization
     */
    private val PLATFORM_TO_BASE_DOMAIN = PLATFORM_GROUPS.associate { it.platform to it.baseDomain }

    /**
     * Map platform to all possible domain variations for proper URL stripping
     */
    private val PLATFORM_DOMAIN_VARIATIONS = mapOf(
        StreamingPlatform.YOUTUBE_MUSIC to listOf("music.youtube.com/", "www.youtube.com/", "youtube.com/", "youtu.be/"),
        StreamingPlatform.APPLE_MUSIC to listOf("geo.music.apple.com/", "music.apple.com/", "itunes.apple.com/"),
        StreamingPlatform.AMAZON_MUSIC to listOf("music.amazon.com/", "amazon.com/"),
        StreamingPlatform.SPOTIFY to listOf("open.spotify.com/", "play.spotify.com/", "spotify.com/"),
        StreamingPlatform.DEEZER to listOf("www.deezer.com/", "deezer.com/"),
        StreamingPlatform.TIDAL to listOf("listen.tidal.com/", "tidal.com/"),
        StreamingPlatform.SOUNDCLOUD to listOf("soundcloud.com/", "m.soundcloud.com/"),
        StreamingPlatform.LAST_FM to listOf("www.last.fm/", "last.fm/", "lastfm.com/", "www.lastfm.com/"),
        StreamingPlatform.PANDORA to listOf("www.pandora.com/", "pandora.com/"),
        StreamingPlatform.NAPSTER to listOf("www.napster.com/", "napster.com/"),
        StreamingPlatform.QOBUZ to listOf("www.qobuz.com/", "qobuz.com/"),
        StreamingPlatform.YANDEX_MUSIC to listOf("music.yandex.ru/", "music.yandex.com/", "yandex.ru/"),
        StreamingPlatform.BOOMPLAY to listOf("boomplay.com/", "www.boomplay.com/"),
        StreamingPlatform.ANGHAMI to listOf("anghami.com/", "www.anghami.com/"),
        StreamingPlatform.AUDIOMACK to listOf("www.audiomack.com/", "audiomack.com/"),
        StreamingPlatform.SHAZAM to listOf("www.shazam.com/", "shazam.com/"),
        StreamingPlatform.JIOSAAVN to listOf("www.jiosaavn.com/", "jiosaavn.com/"),
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
    fun fromKey(key: String): StreamingPlatform? {
        return detectPlatform(key)
    }

    /**
     * Remove known tracking query parameters from a URL to normalize it
     * This strips parameters like utm_*, ref, refer, fbclid, gclid, etc.
     */
    private fun stripTrackingParams(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return trimmed
        return try {
            val uri = URI(trimmed)
            val query = uri.rawQuery ?: return trimmed // no query
            val keptParams = query.split("&")
                .mapNotNull { pair ->
                    if (pair.isBlank()) return@mapNotNull null
                    val idx = pair.indexOf('=')
                    val name = if (idx >= 0) pair.take(idx) else pair
                    val shouldRemove = name.startsWith("utm_", ignoreCase = true)
                            || name.equals("fbclid", ignoreCase = true)
                            || name.equals("gclid", ignoreCase = true)
                            || name.equals("ref", ignoreCase = true)
                            || name.equals("refer", ignoreCase = true)
                            || name.equals("_branch_match_id", ignoreCase = true)
                            || name.equals("_utm_id", ignoreCase = true)
                    if (shouldRemove) null else pair
                }
            val newQuery = keptParams.joinToString("&")
            val cleaned = URI(
                uri.scheme,
                uri.authority,
                uri.path,
                newQuery.ifEmpty { null },
                uri.fragment
            ).toString()
            cleaned
        } catch (_: URISyntaxException) {
            // If parsing fails, fallback to manual simple stripping of utm_* pairs
            val base = trimmed.substringBefore('?')
            val query = trimmed.substringAfter('?', missingDelimiterValue = "")
            if (query.isEmpty()) return trimmed
            val kept = query.split('&').filterNot { it.lowercase().startsWith("utm_") || it.lowercase().startsWith("fbclid=") || it.lowercase().startsWith("gclid=") || it.lowercase().startsWith("ref=") || it.lowercase().startsWith("refer=") }
            if (kept.isEmpty()) base else "$base?${kept.joinToString("&")}"
        }
    }

    /**
     * Canonicalizes a streaming URL for persistence and deduplication.
     *
     * Today this is primarily:
     *  - `trim()`
     *  - remove known tracking parameters (utm_*, fbclid, gclid, ref, ...)
     *
     * Keep this as the single source of truth for URL normalization so that
     * DB uniqueness (`streaming_links.url`) matches what we dedupe in-memory.
     */
    fun normalizeUrl(url: String): String = stripTrackingParams(url)

    /**
     * Processes and prioritizes streaming links
     * Prioritizes URLs with platform keywords, "music" subdomain, and smallest length
     *
     * @param links List of StreamingLink objects to process
     * @param useKeyForDetection If true, use platform from StreamingLink (Odesli mode); if false, detect from URL
     * @return Deduplicated and prioritized list of streaming links
     */
    fun processLinksWithPrioritization(
        links: List<StreamingRef>,
        useKeyForDetection: Boolean = false
    ): List<StreamingRef> {
        val linkMap = mutableMapOf<String, LinkData>()

        // Process all links
        for (link in links) {
            val cleanedUrl = stripTrackingParams(link.url)
            // Always detect from URL to validate it actually belongs to the platform
            val platformFromUrl = detectPlatform(cleanedUrl)
            val groupKeyFromUrl = getPlatformGroup(cleanedUrl)

            // In Odesli mode, verify the URL actually matches the claimed platform
            if (useKeyForDetection) {
                val groupKeyFromPlatform = getPlatformGroup(link.platform.name)

                // Skip if URL doesn't contain platform keywords (wrong platform assignment by API)
                if (platformFromUrl == null || groupKeyFromUrl != groupKeyFromPlatform) {
                    log.warn("Skipping mismatched platform: ${link.platform} doesn't match URL: ${link.url}")
                    continue
                }
            }

            if (platformFromUrl == null || cleanedUrl.isBlank() || groupKeyFromUrl == null) {
                log.warn("Skipping unknown platform or missing data: ${link.platform} - ${link.url}")
                continue
            }

            val isMusicVer = isMusicVersion(cleanedUrl)
            val hasPlatformKeyword = cleanedUrl.lowercase().contains(groupKeyFromUrl)
            val urlLength = cleanedUrl.length
            val existing = linkMap[groupKeyFromUrl]

            if (existing == null) {
                // First link for this group
                linkMap[groupKeyFromUrl] = LinkData(cleanedUrl, platformFromUrl, isMusicVer, hasPlatformKeyword, urlLength)
            } else {
                // Priority order:
                // 1. Has platform keyword in URL (spotify.com vs other domains)
                // 2. Is music version (music.youtube.com vs youtube.com)
                // 3. Shortest URL
                val shouldReplace = when {
                    hasPlatformKeyword && !existing.hasPlatformKeyword -> true
                    hasPlatformKeyword == existing.hasPlatformKeyword && isMusicVer && !existing.isMusicVersion -> true
                    hasPlatformKeyword == existing.hasPlatformKeyword && isMusicVer == existing.isMusicVersion && urlLength < existing.urlLength -> true
                    else -> false
                }

                if (shouldReplace) {
                    linkMap[groupKeyFromUrl] = LinkData(cleanedUrl, platformFromUrl, isMusicVer, hasPlatformKeyword, urlLength)
                }
            }
        }

        // Convert to final format and log
        return linkMap.values.map { data ->
            StreamingRef(data.platform, data.url)
        }
    }

    private data class LinkData(
        val url: String,
        val platform: StreamingPlatform,
        val isMusicVersion: Boolean,
        val hasPlatformKeyword: Boolean,
        val urlLength: Int
    )

    fun extractExternalId(platform: StreamingPlatform, url: String): String {
        val cleanedUrl = stripTrackingParams(url)
        var path = cleanedUrl.removePrefix("https://").removePrefix("http://")

        val domainVariations = PLATFORM_DOMAIN_VARIATIONS[platform] ?: emptyList()
        for (domain in domainVariations) {
            if (path.startsWith(domain)) {
                path = path.removePrefix(domain)
                break
            }
        }

        return path
    }

    fun buildUrl(platform: StreamingPlatform, externalId: String): String {
        val baseDomain = PLATFORM_TO_BASE_DOMAIN[platform] ?: ""
        return "https://$baseDomain$externalId"
    }
}