package org.bscm.clients

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bscm.models.StreamingLink
import org.bscm.utils.StreamingPlatformUtils

class OdesliClient(
    private val client: HttpClient,
    private val json: Json
) {
    private val baseUrl = "https://api.song.link/v1-alpha.1"

    @Serializable
    data class OdesliResponse(val linksByPlatform: Map<String, OdesliLink>)

    @Serializable
    data class OdesliLink(val url: String)

    suspend fun resolve(url: String): List<StreamingLink> {
        val response = client.get("$baseUrl/links?url=$url")
        if (!response.status.isSuccess()) return emptyList()

        val data = json.decodeFromString<OdesliResponse>(
            response.bodyAsText()
        )

        return data.linksByPlatform.mapNotNull { (key, value) ->
            StreamingLink(StreamingPlatformUtils.fromKey(key), value.url)
        }
    }
}