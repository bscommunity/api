package org.bscm.services.track.clients

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bscm.services.track.TrackInfoService

class ItunesClient(
    private val client: HttpClient,
    private val json: Json
) {

    private val baseUrl = "https://itunes.apple.com/search"

    @Serializable
    data class ITunesSearchResult(val results: List<ITunesTrack> = emptyList())

    @Serializable
    data class ITunesTrack(
        val trackId: Long,
        val trackName: String,
        val artistName: String,
        val collectionName: String,
        val artworkUrl100: String,
        val primaryGenreName: String,
        val genreNames: List<String> = emptyList(),
        val trackViewUrl: String,
        val collectionViewUrl: String? = null,
        val previewUrl: String? = null,
        val trackCount: Int? = null,
        val releaseDate: String? = null,
        val trackExplicitness: String? = null,
        val isrc: String? = null
    ) {
        fun matchScore(ctx: TrackInfoService.TrackMatchContext): Int {
            var score = 0

            val trackName = trackName.lowercase()
            val artistName = artistName.lowercase()
            val collection = collectionName.lowercase()

            val targetTrack = ctx.track.lowercase()
            val targetArtist = ctx.artist.lowercase()

            // 🎵 Track name
            score += when {
                trackName == targetTrack -> 100
                trackName.contains(targetTrack) -> 70
                targetTrack.contains(trackName) -> 50
                else -> 0
            }

            // 👤 Artist
            score += when {
                artistName == targetArtist -> 80
                artistName.contains(targetArtist) ||
                        targetArtist.contains(artistName) -> 60
                else -> 0
            }

            // 🚫 Remix / edit penalty
            val remixPatterns = Regex(
                "\\b(remix|edit|version|remaster|acoustic|live|instrumental|radio|extended|club|dub|vip)\\b",
                RegexOption.IGNORE_CASE
            )

            if (remixPatterns.containsMatchIn(trackName)) {
                score -= 30
            }

            // 💿 Single bonus
            if (collection.contains("single") || (trackCount ?: 0) == 1) {
                score += 20
            }

            // 🎚 Prefer original versions
            if (!collection.contains("remix") && !collection.contains("edit")) {
                score += 15
            }

            // 📅 Recency bonus
            val releaseYear =
                releaseDate
                    ?.takeIf { it.length >= 10 }
                    ?.substring(0, 10)
                    ?.let { LocalDate.parse(it).year }
                    ?: 0

            if (releaseYear >= Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year - 2) {
                score += 5
            }

            return score
        }
    }

    suspend fun search(track: String, artist: String): List<ITunesTrack> {
        val query = listOf(
            "term" to "$track $artist",
            "media" to "music",
            "entity" to "song",
            "limit" to "5"
        ).formUrlEncode()

        val response = client.get("$baseUrl?$query")
        if (!response.status.isSuccess()) return emptyList()

        return json.decodeFromString<ITunesSearchResult>(
            response.bodyAsText()
        ).results
    }

    suspend fun getTrack(id: String): ITunesTrack? {
        val response = client.get("https://itunes.apple.com/lookup?id=$id")
        if (!response.status.isSuccess()) return null

        val results = json.decodeFromString<ITunesSearchResult>(
            response.bodyAsText()
        ).results

        return results.firstOrNull()
    }
}

fun List<ItunesClient.ITunesTrack>.bestMatch(ctx: TrackInfoService.TrackMatchContext): ItunesClient.ITunesTrack? =
    this
        .map { track -> track to track.matchScore(ctx) }
        .filter { (_, score) -> score > 0 }
        .maxByOrNull { (_, score) -> score }
        ?.first