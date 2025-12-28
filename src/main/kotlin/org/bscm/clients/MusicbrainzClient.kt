package org.bscm.clients

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bscm.models.StreamingLink
import org.bscm.utils.StreamingPlatformUtils

class MusicbrainzClient(
    private val client: HttpClient,
    private val json: Json
) {
    private val baseUrl = "https://musicbrainz.org/ws/2"

    @Serializable
    private data class MBRecording(val id: String? = null)

    @Serializable
    private data class MBUrl(val resource: String? = null)

    @Serializable
    private data class MBRelation(val type: String? = null, val url: MBUrl? = null)

    @Serializable
    private data class MBRecordingResponse(val recordings: List<MBRecording> = emptyList())

    @Serializable
    private data class MBRecordingDetailsResponse(val relations: List<MBRelation> = emptyList())

    private suspend fun fetchMusicBrainzRecordingIds(query: String): List<String> {
        val response = client.get("$baseUrl/recording?query=$query&fmt=json") {
            header("User-Agent", "bscm/1.0 (app.bscm@gmail.com)")
        }
        if (!response.status.isSuccess()) return emptyList()

        val data = json.decodeFromString<MBRecordingResponse>(response.bodyAsText())
        return data.recordings.mapNotNull { it.id }
    }

    private suspend fun fetchMusicBrainzRelations(recordingId: String): List<MBRelation> {
        val response = client.get("$baseUrl/recording/$recordingId?inc=url-rels&fmt=json") {
            header("User-Agent", "bscm/1.0 (app.bscm@gmail.com)")
        }
        if (!response.status.isSuccess()) return emptyList()

        val data = json.decodeFromString<MBRecordingDetailsResponse>(response.bodyAsText())
        return data.relations
    }

    suspend fun resolve(query: String): List<StreamingLink> {
        val recordingIds = fetchMusicBrainzRecordingIds(query)
        if (recordingIds.isEmpty()) return emptyList()

        val streamingLinks = mutableListOf<StreamingLink>()
        for (recordingId in recordingIds) {
            val relations = fetchMusicBrainzRelations(recordingId)
            relations.forEach { relation ->
                relation.url?.resource?.let { url ->
                    val platform = StreamingPlatformUtils.fromKey(relation.type ?: "unknown")
                    streamingLinks.add(StreamingLink(platform, url))
                }
            }
        }
        return streamingLinks
    }
}
