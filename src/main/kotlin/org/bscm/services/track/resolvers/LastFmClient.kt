package org.bscm.services.track.resolvers

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class LastFmClient(
    private val apiKey: String,
    private val client: HttpClient,
    private val json: Json
) {

    private val baseUrl = "http://ws.audioscrobbler.com/2.0/"

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
}