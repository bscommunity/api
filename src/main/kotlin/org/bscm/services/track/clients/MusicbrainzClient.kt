package org.bscm.services.track.clients

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.logging.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bscm.models.StreamingRef
import org.bscm.utils.StreamingPlatformUtils

class MusicbrainzClient(
    private val client: HttpClient,
    private val json: Json
) {
    private val logger = KtorSimpleLogger("MusicbrainzClient")
    private val baseUrl = "https://musicbrainz.org/ws/2"

    @Serializable
    private data class MBRecording(
        val id: String? = null,
        val score: Int? = null,
        val disambiguation: String? = null,
        val releases: List<MBRelease> = emptyList(),
        val relations: List<MBRelation> = emptyList()
    )

    @Serializable
    private data class MBRelease(
        val id: String? = null,
        val status: String? = null
    )

    @Serializable
    private data class MBUrl(val resource: String? = null)

    @Serializable
    private data class MBRelation(val type: String? = null, val url: MBUrl? = null)

    @Serializable
    private data class MBRecordingResponse(val recordings: List<MBRecording> = emptyList())

    @Serializable
    private data class MBReleaseDetailsResponse(val relations: List<MBRelation> = emptyList())

    private suspend fun fetchMusicBrainzRecordings(query: String): List<MBRecording> {
        val response = client.get("$baseUrl/recording") {
            parameter("query", query)
            parameter("fmt", "json")
            parameter("limit", "5")
            header("User-Agent", "bscm/1.0 (app.bscm@gmail.com)")
        }
        if (!response.status.isSuccess()) return emptyList()

        val data = json.decodeFromString<MBRecordingResponse>(response.bodyAsText())
        return data.recordings
    }

    private suspend fun fetchReleaseRelations(releaseId: String): List<MBRelation> {
        val response = client.get("$baseUrl/release/$releaseId") {
            parameter("inc", "url-rels")
            header("User-Agent", "bscm/1.0 (app.bscm@gmail.com)")
        }
        if (!response.status.isSuccess()) return emptyList()

        val data = json.decodeFromString<MBReleaseDetailsResponse>(response.bodyAsText())
        return data.relations
    }

    private fun relationsToStreamingRefs(relations: List<MBRelation>): List<StreamingRef> {
        return relations.mapNotNull { relation ->
            relation.url?.resource?.let { url ->
                StreamingPlatformUtils.fromKey(url)?.let { platform ->
                    StreamingRef(platform, url)
                }
            }
        }
    }

    suspend fun resolve(query: String): List<StreamingRef> {
        val recordings = fetchMusicBrainzRecordings(query)
        if (recordings.isEmpty()) return emptyList()

        val bestMatch = recordings
            .filter { it.disambiguation.isNullOrBlank() }
            .maxByOrNull { it.score ?: 0 }
            ?: recordings.maxByOrNull { it.score ?: 0 }

        val recordingId = bestMatch?.id ?: return emptyList()

        val recordingDetailsResponse = client.get("$baseUrl/recording/$recordingId") {
            parameter("fmt", "json")
            parameter("inc", "releases url-rels")
            header("User-Agent", "bscm/1.0 (app.bscm@gmail.com)")
        }
        if (!recordingDetailsResponse.status.isSuccess()) return emptyList()

        val recordingDetails = json.decodeFromString<MBRecording>(recordingDetailsResponse.bodyAsText())

        if (recordingDetails.relations.isNotEmpty()) {
            val refs = relationsToStreamingRefs(recordingDetails.relations)
            if (refs.isNotEmpty()) return refs
        }

        logger.debug("No direct recording relations for $recordingId, trying Official release")

        val officialRelease = recordingDetails.releases.firstOrNull { it.status == "Official" }
        val releaseId = officialRelease?.id ?: recordingDetails.releases.firstOrNull()?.id
        if (releaseId == null) return emptyList()

        val releaseRelations = fetchReleaseRelations(releaseId)
        return relationsToStreamingRefs(releaseRelations)
    }
}
