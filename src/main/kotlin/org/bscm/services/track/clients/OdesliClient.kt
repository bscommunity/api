package org.bscm.services.track.clients

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bscm.models.StreamingRef
import org.bscm.utils.StreamingPlatformUtils

class OdesliClient(
    private val client: HttpClient,
    private val json: Json
) {
    private val baseUrl = "https://api.song.link/v1-alpha.1"

    @Serializable
    data class OdesliResponse(val linksByPlatform: Map<String, OdesliLink>, val isrc: String? = null)

    @Serializable
    data class OdesliLink(val url: String)

    suspend fun resolve(url: String): List<StreamingRef> {
        val response = client.get("$baseUrl/links?url=$url")
        if (!response.status.isSuccess()) return emptyList()

        val data = json.decodeFromString<OdesliResponse>(
            response.bodyAsText()
        )

        return data.linksByPlatform.mapNotNull { (key, value) ->
            StreamingPlatformUtils.fromKey(key)?.let { platform ->
                StreamingRef(platform, value.url)
            }
        }
    }
}