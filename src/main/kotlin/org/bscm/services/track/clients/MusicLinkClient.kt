package org.bscm.services.track.clients

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.bscm.models.StreamingRef
import org.bscm.utils.StreamingPlatformUtils

class MusicLinkClient(
    private val client: HttpClient,
    private val json: Json,
    private val apiKey: String? = null
) {
    private val htmlBaseUrl = "https://ml.jadquir.com"
    private val apiBaseUrl = "https://api.ml.jadquir.com/v1"

    @Serializable
    private data class MusicLinkApiLinks(
        val musiclink: String? = null,
        val spotify: String? = null,
        val youtube: String? = null,
        @SerialName("youtube_music") val youtubeMusic: String? = null,
        val deezer: String? = null,
        val soundcloud: String? = null,
        @SerialName("apple_music") val appleMusic: String? = null,
        val tidal: String? = null,
        val qobuz: String? = null,
        val audius: String? = null,
        val shazam: String? = null,
        val yandex: String? = null,
        val anghami: String? = null,
        val napster: String? = null,
        val pandora: String? = null,
        val boomplay: String? = null,
        val audiomack: String? = null,
        @SerialName("amazon_music") val amazonMusic: String? = null,
        @SerialName("amazon_store") val amazonStore: String? = null
    )

    @Serializable
    private data class MusicLinkApiTrack(
        val title: String? = null,
        val artist: String? = null,
        val isrc: String? = null,
        @SerialName("image_url") val imageUrl: String? = null,
        val links: MusicLinkApiLinks? = null
    )

    @Serializable
    private data class MusicLinkApiResponse(
        val success: Boolean = false,
        val data: List<MusicLinkApiTrack> = emptyList()
    )

    fun normalizeForUrl(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s-]"), "")
            .replace(Regex("\\s+"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
    }

    suspend fun resolve(artist: String, track: String, isrc: String? = null): MusicLinkResult {
        // 1. Try HTML scraping first
        val scraped = tryScrapeHtml(artist, track)
        if (scraped != null) return scraped

        // 2. Fallback to API by ISRC
        if (apiKey != null && !isrc.isNullOrBlank()) {
            val apiResult = apiLookupByIsrc(isrc)
            if (apiResult != null) return apiResult
        }

        return MusicLinkResult(emptyList(), null)
    }

    suspend fun resolveByPlatformUrl(platformUrl: String): MusicLinkResult {
        val (platform, id) = parsePlatformUrl(platformUrl) ?: return MusicLinkResult(emptyList(), null)
        return apiLookupByPlatform(platform, id) ?: MusicLinkResult(emptyList(), null)
    }

    private fun parsePlatformUrl(url: String): Pair<String, String>? {
        val stripped = url.removePrefix("https://").removePrefix("http://")

        // Apple Music: music.apple.com/us/album/.../1867307508?i=1867307509 or geo.music.apple.com/...
        if (stripped.contains("music.apple.com/") || stripped.contains("itunes.apple.com/")) {
            // Extract the ?i= parameter (track ID), or fall back to last path segment
            val trackId = Regex("""[?&]i=(\d+)""").find(url)?.groupValues?.get(1)
                ?: stripped.split("/").lastOrNull { it.all { c -> c.isDigit() } }
            if (trackId != null) return "apple" to trackId
        }

        // Deezer: www.deezer.com/track/3761677052 or deezer.com/track/...
        if (stripped.contains("deezer.com/")) {
            val trackId = Regex("""/track/(\d+)""").find(stripped)?.groupValues?.get(1)
            if (trackId != null) return "deezer" to trackId
        }

        return null
    }

    private suspend fun apiLookupByPlatform(platform: String, id: String): MusicLinkResult? {
        if (apiKey == null) return null
        return try {
            val response = client.get("$apiBaseUrl/lookup/$platform/$id") {
                header("Authorization", "Bearer $apiKey")
            }
            if (!response.status.isSuccess()) return null

            parseApiResponse(response.bodyAsText())
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun tryScrapeHtml(artist: String, track: String): MusicLinkResult? {
        val normalizedArtist = normalizeForUrl(artist)
        val normalizedTrack = normalizeForUrl(track)
        val url = "$htmlBaseUrl/song/$normalizedArtist/$normalizedTrack"

        return try {
            val response = client.get(url)
            if (!response.status.isSuccess()) return null

            val html = response.bodyAsText()
            extractJsonLdFromHtml(html)
        } catch (_: Exception) {
            null
        }
    }

    private fun extractJsonLdFromHtml(html: String): MusicLinkResult? {
        val pattern = Regex("""<script[^>]*>self\.__next_f\.push\(\[1,"(.+?)"\]\)</script>""", RegexOption.DOT_MATCHES_ALL)

        for (match in pattern.findAll(html)) {
            val jsonStr = match.groupValues[1]
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")

            if (!jsonStr.contains("MusicRecording")) continue

            return try {
                val element = json.parseToJsonElement(jsonStr).jsonObject
                val sameAs = element["sameAs"]
                    ?.let { json.decodeFromString<List<String>>(it.toString()) }
                    ?: emptyList()
                val isrc = element["isrcCode"]?.jsonPrimitive?.content

                val links = sameAs.mapNotNull { url ->
                    StreamingPlatformUtils.fromKey(url)?.let { platform ->
                        StreamingRef(platform, url)
                    }
                }

                MusicLinkResult(links, isrc)
            } catch (_: Exception) {
                continue
            }
        }

        return null
    }

    private suspend fun apiLookupByIsrc(isrc: String): MusicLinkResult? {
        return try {
            val response = client.get("$apiBaseUrl/lookup/isrc/$isrc") {
                header("Authorization", "Bearer $apiKey")
            }
            if (!response.status.isSuccess()) return null

            parseApiResponse(response.bodyAsText())
        } catch (_: Exception) {
            null
        }
    }

    private fun parseApiResponse(body: String): MusicLinkResult? {
        return try {
            val response = json.decodeFromString<MusicLinkApiResponse>(body)
            if (!response.success || response.data.isEmpty()) return null

            val track = response.data.first()
            val links = track.links?.let { apiLinksToStreamingRefs(it) } ?: emptyList()

            MusicLinkResult(links, track.isrc)
        } catch (_: Exception) {
            null
        }
    }

    private fun apiLinksToStreamingRefs(links: MusicLinkApiLinks): List<StreamingRef> {
        val allUrls = listOf(
            links.spotify,
            links.youtube,
            links.youtubeMusic,
            links.deezer,
            links.soundcloud,
            links.appleMusic,
            links.tidal,
            links.qobuz,
            links.audius,
            links.shazam,
            links.yandex,
            links.anghami,
            links.napster,
            links.pandora,
            links.boomplay,
            links.audiomack,
            links.amazonMusic
        ).filterNotNull()

        return allUrls.mapNotNull { url ->
            StreamingPlatformUtils.fromKey(url)?.let { platform ->
                StreamingRef(platform, url)
            }
        }
    }

    data class MusicLinkResult(
        val links: List<StreamingRef>,
        val isrc: String?
    )
}
