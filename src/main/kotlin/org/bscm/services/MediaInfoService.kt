package org.bscm.services

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.bscm.models.StreamingLink
import org.bscm.models.enums.Genre
import org.bscm.models.enums.StreamingPlatform
import org.bscm.utils.GenresUtils
import org.bscm.utils.StreamingPlatformUtils
import java.time.LocalDate

object MediaInfoService {
    // API Keys (replace with your config management)
    private const val LASTFM_API_KEY = "YOUR_LASTFM_API_KEY"

    // API URLs
    private const val ITUNES_API_URL = "https://itunes.apple.com/search"
    private const val ODESLI_API_URL = "https://api.song.link/v1-alpha.1"
    private const val LASTFM_API_URL = "http://ws.audioscrobbler.com/2.0/"
    private const val MUSICBRAINZ_API_URL = "https://musicbrainz.org/ws/2/recording"

    private val client = HttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class ITunesSearchResult(val results: List<ITunesResponse> = emptyList())
    @Serializable
    data class ITunesResponse(
        val trackName: String,
        val artistName: String,
        val collectionName: String,
        val artworkUrl100: String,
        val primaryGenreName: String,
        val trackViewUrl: String,
        val previewUrl: String? = null,
        val trackCount: Int? = null,
        val releaseDate: String? = null
    )

    @Serializable
    data class LastFMResponse(val track: LastFMTrack)
    @Serializable
    data class LastFMTrack(
        val name: String,
        val artist: LastFMArtist,
        val album: LastFMAlbum? = null,
        val url: String,
        val toptags: LastFMTags? = null
    )
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
    data class OdesliResponse(val linksByPlatform: Map<String, OdesliLink>)
    @Serializable
    data class OdesliLink(val url: String)

    @Serializable
    data class MediaInfoModel(
        val coverUrl: String?,
        val album: String?,
        val track: String?,
        val artist: String?,
        val genre: Genre?,
        val trackUrls: List<StreamingLink>,
        val trackPreviewUrl: String? = null
    )

    fun cleanTrackName(track: String): String {
        return track
            .replace(Regex("""\(.*?\)"""), "") // Remove content in parentheses
            .replace(Regex("""\[.*?]"""), "") // Remove content in brackets
            .replace(Regex("""feat\.|ft\.|featuring""", RegexOption.IGNORE_CASE), "") // Remove featuring
            .replace(
                Regex("""remix|edit|version|remaster(ed)?""", RegexOption.IGNORE_CASE),
                ""
            ) // Remove version indicators
            .replace(Regex("""part\.?\s*\d+""", RegexOption.IGNORE_CASE), "") // Remove "part X"
            .replace(Regex("""\s+"""), " ") // Remove extra spaces
            .trim()
    }

    fun cleanArtistName(artist: String): String {
        return artist
            .replace(Regex("feat\\.|ft\\.|featuring", RegexOption.IGNORE_CASE), "")
            .replace("&amp;", "&")
            .replace(Regex("\\s*,\\s*"), " & ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun findBestTrackMatch(tracks: List<ITunesResponse>, targetTrack: String, targetArtist: String): ITunesResponse? {
        if (tracks.isEmpty()) return null
        return tracks.maxByOrNull { calculateTrackScore(it, targetTrack, targetArtist) }
    }

    fun calculateTrackScore(track: ITunesResponse, targetTrack: String, targetArtist: String): Int {
        var score = 0
        val trackName = track.trackName.lowercase()
        val artistName = track.artistName.lowercase()
        val collectionName = track.collectionName.lowercase()
        val normalizedTargetTrack = targetTrack.lowercase()
        val normalizedTargetArtist = targetArtist.lowercase()
        score += when {
            trackName == normalizedTargetTrack -> 100
            trackName.contains(normalizedTargetTrack) -> 70
            normalizedTargetTrack.contains(trackName) -> 50
            else -> 0
        }
        score += when {
            artistName == normalizedTargetArtist -> 80
            artistName.contains(normalizedTargetArtist) || normalizedTargetArtist.contains(artistName) -> 60
            else -> 0
        }
        val remixPatterns = Regex("\\b(remix|edit|version|remaster|acoustic|live|instrumental|radio|extended|club|dub|vip)\\b", RegexOption.IGNORE_CASE)
        if (remixPatterns.containsMatchIn(trackName)) score -= 30
        if (collectionName.contains("single") || (track.trackCount ?: 0) == 1) score += 20
        if (!collectionName.contains("remix") && !collectionName.contains("edit")) score += 15
        val releaseYear = track.releaseDate?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it.substring(0, 10)).year } ?: 0
        if (releaseYear >= LocalDate.now().year - 2) score += 5
        return score
    }

    suspend fun getMediaInfo(track: String, artist: String): MediaInfoModel {
        val cleanedTrack = cleanTrackName(track)
        val cleanedArtist = cleanArtistName(artist)
        var foundGenre: Genre? = null
        val errors = mutableListOf<String>()
        try {
            val query = listOf("term" to "$cleanedTrack $cleanedArtist", "entity" to "song", "limit" to "5").formUrlEncode()
            val iTunesResults = fetchItunesApi(query)
            val bestMatch = findBestTrackMatch(iTunesResults, cleanedTrack, cleanedArtist)
            if (bestMatch != null) {
                foundGenre = GenresUtils.normalizeGenre(bestMatch.primaryGenreName)
                return MediaInfoModel(
                    coverUrl = bestMatch.artworkUrl100.replace("100x100", "600x600"),
                    album = bestMatch.collectionName,
                    track = bestMatch.trackName,
                    artist = bestMatch.artistName,
                    genre = foundGenre,
                    trackUrls = listOf(StreamingLink(StreamingPlatform.APPLE_MUSIC, bestMatch.trackViewUrl)),
                    trackPreviewUrl = bestMatch.previewUrl
                )
            }
        } catch (e: Exception) { errors.add(e.message ?: "iTunes error") }

        try {
            val query = listOf(
                "method" to "track.getInfo",
                "artist" to cleanedArtist,
                "track" to cleanedTrack,
                "api_key" to LASTFM_API_KEY,
                "format" to "json"
            ).formUrlEncode()
            val lastFmData = fetchLastfmApi(query)
            if (lastFmData.track.album != null) {
                val tags = lastFmData.track.toptags?.tag ?: emptyList()
                tags.firstOrNull { GenresUtils.normalizeGenre(it.name) != null }?.let { tag ->
                    foundGenre = GenresUtils.normalizeGenre(tag.name)
                }
                return MediaInfoModel(
                    coverUrl = lastFmData.track.album.image.getOrNull(3)?.url,
                    album = lastFmData.track.album.title,
                    track = lastFmData.track.name,
                    artist = lastFmData.track.artist.name,
                    genre = foundGenre,
                    trackUrls = listOf(StreamingLink(StreamingPlatform.LAST_FM, lastFmData.track.url)),
                    trackPreviewUrl = null
                )
            }
        } catch (e: Exception) { errors.add(e.message ?: "Last.fm error") }

        try {
            val veryCleanTrack = cleanedTrack.split("-")[0].trim()
            val partialArtist = cleanedArtist.split("&")[0].trim()
            val query = listOf("term" to "$veryCleanTrack $partialArtist", "entity" to "song", "limit" to "5").formUrlEncode()
            val iTunesResults = fetchItunesApi(query)
            val bestMatch = findBestTrackMatch(iTunesResults, veryCleanTrack, partialArtist)
            if (bestMatch != null) {
                foundGenre = GenresUtils.normalizeGenre(bestMatch.primaryGenreName) ?: foundGenre
                return MediaInfoModel(
                    coverUrl = bestMatch.artworkUrl100.replace("100x100", "600x600"),
                    album = bestMatch.collectionName,
                    track = bestMatch.trackName,
                    artist = bestMatch.artistName,
                    genre = foundGenre,
                    trackUrls = listOf(StreamingLink(StreamingPlatform.APPLE_MUSIC, bestMatch.trackViewUrl)),
                    trackPreviewUrl = bestMatch.previewUrl
                )
            }
        } catch (e: Exception) { errors.add(e.message ?: "iTunes fallback error") }

        throw Exception("Unable to find information for '$track' by '$artist'. Details: ${errors.joinToString("; ")}")
    }

    suspend fun getTrackStreamingLinks(url: String, track: String, artist: String): List<StreamingLink> {
        try {
            val odesliData = fetchOdesliApi(url)
            val odesliLinks = odesliData.linksByPlatform.map { (key, value) -> StreamingLink(StreamingPlatformUtils.fromKey(key), value.url) }
            // println("Found ${odesliLinks.size} links via Odesli")
            return StreamingPlatformUtils.processLinksWithPrioritization(odesliLinks, true)
        } catch (error: Exception) {
            println("Odesli fetch error: ${error.message}")
        }

        try {
            val recordingIds = fetchMusicBrainzRecordingIds("recording:\"$track\" AND artist:\"$artist\"")
            val firstId = recordingIds.firstOrNull() ?: return emptyList()
            val relations = fetchMusicBrainzRelations(firstId)
            val musicBrainzLinks = relations.filter { it.type == "free streaming" && it.url?.resource != null }.map {
                val link = it.url!!.resource!!
                StreamingLink(StreamingPlatformUtils.fromUrl(link), link)
            }
            // println("Found ${musicBrainzLinks.size} links via MusicBrainz")
            return StreamingPlatformUtils.processLinksWithPrioritization(musicBrainzLinks, false)
        } catch (error: Exception) {
            println("MusicBrainz fetch error: ${error.message}")
        }

        throw Exception("No streaming links found for track")
    }

    private suspend fun fetchItunesApi(query: String): List<ITunesResponse> {
        val response: HttpResponse = client.get("$ITUNES_API_URL?$query")
        if (!response.status.isSuccess()) throw Exception("iTunes API error: ${response.status.description}")
        return json.decodeFromString<ITunesSearchResult>(response.bodyAsText()).results
    }

    private suspend fun fetchOdesliApi(url: String): OdesliResponse {
        val response: HttpResponse = client.get("$ODESLI_API_URL/links?url=$url")
        if (!response.status.isSuccess()) throw Exception("Odesli API error: ${response.status.description}")
        return json.decodeFromString<OdesliResponse>(response.bodyAsText())
    }

    private suspend fun fetchLastfmApi(query: String): LastFMResponse {
        val response: HttpResponse = client.get("$LASTFM_API_URL?$query")
        if (!response.status.isSuccess()) throw Exception("Last.fm API error: ${response.status.description}")
        val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
        if (root["message"] != null) throw Exception(root["message"]!!.jsonPrimitive.content)
        return json.decodeFromString<LastFMResponse>(response.bodyAsText())
    }

    // MusicBrainz lightweight JSON parsing
    @Serializable
    private data class MBRecording(val id: String? = null)
    @Serializable
    private data class MBUrl(val resource: String? = null)
    @Serializable
    private data class MBRelation(val type: String? = null, val url: MBUrl? = null)

    private suspend fun fetchMusicBrainzRecordingIds(query: String): List<String> {
        val response: HttpResponse = client.get("$MUSICBRAINZ_API_URL?${query}&fmt=json") {
            header("User-Agent", "bscm/1.0 (app.bscm@gmail.com)")
        }
        if (!response.status.isSuccess()) throw Exception("MusicBrainz API error: ${response.status.description}")
        val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val recordings = root["recordings"] as? JsonArray ?: return emptyList()
        return recordings.mapNotNull { recEl -> recEl.jsonObject["id"]?.jsonPrimitive?.content }
    }
    private suspend fun fetchMusicBrainzRelations(recordingId: String): List<MBRelation> {
        val response: HttpResponse = client.get("$MUSICBRAINZ_API_URL/$recordingId?inc=url-rels&fmt=json") {
            header("User-Agent", "bscm/1.0 (app.bscm@gmail.com)")
        }
        if (!response.status.isSuccess()) throw Exception("MusicBrainz API error: ${response.status.description}")
        val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val relationsEl = root["relations"] as? JsonArray ?: return emptyList()
        return relationsEl.map { relEl ->
            val obj = relEl.jsonObject
            val type = obj["type"]?.jsonPrimitive?.content
            val url = obj["url"]?.jsonObject?.get("resource")?.jsonPrimitive?.content
            MBRelation(type = type, url = MBUrl(url))
        }
    }
}
