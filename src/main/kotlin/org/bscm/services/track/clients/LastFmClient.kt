package org.bscm.services.track.clients

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.logging.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.bscm.models.StreamingRef
import org.bscm.models.enums.StreamingPlatform

class LastFmClient(
    private val apiKey: String,
    private val client: HttpClient,
    private val json: Json
) {

    private val logger = KtorSimpleLogger("LastFmClient")
    private val baseUrl = "http://ws.audioscrobbler.com/2.0/"

    private val playlinkAffiliateRegex = Regex(
        """data-playlink-affiliate="([^"]+)""""
    )
    private val playlinkClassRegex = Regex(
        """play-this-track-playlink--(\w+)"""
    )
    private val hrefRegex = Regex(
        """href="(https?://[^"]+)""""
    )

    @Serializable
    data class LastFMResponse(val track: LastFMTrack)

    @Serializable
    data class LastFMArtist(val name: String)

    @Serializable
    data class LastFMAlbum(val title: String, val image: List<LastFMImage>)

    @Serializable
    data class LastFMImage(@SerialName("#text") val url: String? = null)

    @Serializable
    data class LastFMTags(val tag: List<LastFMTag>)

    @Serializable
    data class LastFMTag(val name: String)

    @Serializable
    data class LastFMTrack(
        val name: String,
        val artist: LastFMArtist,
        val album: LastFMAlbum? = null,
        val url: String,
        val toptags: LastFMTags? = null
    )

    suspend fun getTrackInfo(track: String, artist: String): LastFMTrack? {
        val query = listOf(
            "method" to "track.getInfo",
            "artist" to artist,
            "track" to track,
            "api_key" to apiKey,
            "autocorrect" to "1",
            "format" to "json"
        ).formUrlEncode()

        val response = client.get("$baseUrl?$query")
        if (!response.status.isSuccess()) return null

        val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
        if (root["message"] != null) return null

        return json.decodeFromString<LastFMResponse>(
            response.bodyAsText()
        ).track
    }

    suspend fun getTrackStreamingLinks(trackUrl: String): List<StreamingRef> {
        val response = client.get(trackUrl) {
            header("User-Agent", "Mozilla/5.0 (compatible; bscm/1.0)")
        }
        if (!response.status.isSuccess()) return emptyList()

        val html = response.bodyAsText()
        val sectionStart = html.indexOf("Play this track")
            .takeIf { it >= 0 } ?: return emptyList()
        val sectionHtml = html.substring(sectionStart, (sectionStart + 5000).coerceAtMost(html.length))

        val playlinks = mutableListOf<StreamingRef>()
        val liSegments = sectionHtml.split("<li>")
            .drop(1)

        for (segment in liSegments) {
            val affiliate = playlinkAffiliateRegex.find(segment)?.groupValues?.get(1)
                ?: playlinkClassRegex.find(segment)?.groupValues?.get(1)
                ?: continue
            val href = hrefRegex.find(segment)?.groupValues?.get(1) ?: continue
            val platform = AFFILIATE_TO_PLATFORM[affiliate] ?: continue
            playlinks.add(StreamingRef(platform, href))
        }

        logger.debug("Scraped ${playlinks.size} playlinks from Last.fm page: ${playlinks.map { it.platform }}")
        return playlinks
    }

    companion object {
        private val AFFILIATE_TO_PLATFORM = mapOf(
            "youtube" to StreamingPlatform.YOUTUBE_MUSIC,
            "spotify" to StreamingPlatform.SPOTIFY,
            "itunes" to StreamingPlatform.APPLE_MUSIC,
        )
    }
}