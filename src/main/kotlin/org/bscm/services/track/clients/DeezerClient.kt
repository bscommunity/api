package org.bscm.services.track.clients

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder

class DeezerClient(
    private val client: HttpClient,
    private val json: Json
) {
    private val baseUrl = "https://api.deezer.com/search"

    @Serializable
    data class DeezerSearchResult(val data: List<DeezerTrack> = emptyList(), val total: Int = 0)

    @Serializable
    data class DeezerArtist(val id: Long? = null, val name: String? = null)

    @Serializable
    data class DeezerAlbum(
        val id: Long? = null,
        val title: String? = null,
        val cover: String? = null,
        val cover_medium: String? = null,
        val cover_big: String? = null,
        val cover_xl: String? = null
    )

    @Serializable
    data class DeezerTrack(
        val id: Long,
        val title: String,
        val link: String,
        val preview: String? = null,
        val artist: DeezerArtist? = null,
        val album: DeezerAlbum? = null,
        @SerialName("explicit_lyrics") val explicitLyrics: Boolean? = false,
        val isrc: String? = null
    )

    suspend fun search(track: String, artist: String): List<DeezerTrack> {
        val rawQuery = "track:\"$track\" artist:\"$artist\""
        val encoded = URLEncoder.encode(rawQuery, Charsets.UTF_8.name())

        val response = client.get("$baseUrl?q=$encoded")
        if (!response.status.isSuccess()) return emptyList()

        return json.decodeFromString<DeezerSearchResult>(
            response.bodyAsText()
        ).data
    }

    suspend fun getTrack(id: String): DeezerTrack? {
        val response = client.get("https://api.deezer.com/track/$id")
        if (!response.status.isSuccess()) return null

        return json.decodeFromString<DeezerTrack>(
            response.bodyAsText()
        )
    }
}
