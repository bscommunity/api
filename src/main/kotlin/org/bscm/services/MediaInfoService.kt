package org.bscm.services

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bscm.models.StreamingLink
import org.bscm.models.enums.Genre
import org.bscm.models.enums.StreamingPlatform
import org.bscm.utils.GenresUtils
import org.bscm.utils.StreamingPlatformUtils
import java.time.LocalDate

object MediaInfoService {
    // API Keys (replace with your config management)
    private const val LASTFM_API_KEY = "YOUR_LASTFM_API_KEY"
    private const val SPOTIFY_API_KEY = "YOUR_SPOTIFY_API_KEY"
    private const val SPOTIFY_CLIENT_ID = "YOUR_SPOTIFY_CLIENT_ID"

    // API URLs
    private const val ITUNES_API_URL = "https://itunes.apple.com/search"
    private const val ODESLI_API_URL = "https://api.song.link/v1-alpha.1"
    private const val LASTFM_API_URL = "http://ws.audioscrobbler.com/2.0/"
    private const val MUSICBRAINZ_API_URL = "https://musicbrainz.org/ws/2/recording"

    private val client = HttpClient()

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
    data class LastFMResponse(
        val track: LastFMTrack
    )

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
    data class LastFMImage(val text: String)

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
        val scoredTracks = tracks.map {
            it to calculateTrackScore(it, targetTrack, targetArtist)
        }
        return scoredTracks.maxByOrNull { it.second }?.first
    }

    fun calculateTrackScore(track: ITunesResponse, targetTrack: String, targetArtist: String): Int {
        var score = 0
        val trackName = track.trackName.lowercase()
        val artistName = track.artistName.lowercase()
        val collectionName = track.collectionName.lowercase()
        val normalizedTargetTrack = targetTrack.lowercase()
        val normalizedTargetArtist = targetArtist.lowercase()
        if (trackName == normalizedTargetTrack) score += 100
        else if (trackName.contains(normalizedTargetTrack)) score += 70
        else if (normalizedTargetTrack.contains(trackName)) score += 50
        if (artistName == normalizedTargetArtist) score += 80
        else if (artistName.contains(normalizedTargetArtist) || normalizedTargetArtist.contains(artistName)) score += 60
        val remixPatterns = Regex(
            "\\b(remix|edit|version|remaster|acoustic|live|instrumental|radio|extended|club|dub|vip)\\b",
            RegexOption.IGNORE_CASE
        )
        if (remixPatterns.containsMatchIn(trackName)) score -= 30
        if (collectionName.contains("single") || (track.trackCount ?: 0) == 1) score += 20
        if (!collectionName.contains("remix") && !collectionName.contains("edit")) score += 15
        val releaseYear =
            track.releaseDate?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it.substring(0, 10)).year } ?: 0
        val currentYear = LocalDate.now().year
        if (releaseYear >= currentYear - 2) score += 5
        return score
    }

    suspend fun getMediaInfo(track: String, artist: String): MediaInfoModel {
        val cleanedTrack = cleanTrackName(track)
        val cleanedArtist = cleanArtistName(artist)
        var foundGenre: Genre? = null
        val errors = mutableListOf<String>()
        // Try iTunes
        try {
            val query = listOf(
                "term" to "$cleanedTrack $cleanedArtist",
                "entity" to "song",
                "limit" to "5"
            ).formUrlEncode()
            val iTunesResults = fetchItunesApi(query)
            if (iTunesResults.isNotEmpty()) {
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
            }
        } catch (e: Exception) {
            errors.add(e.message ?: "iTunes error")
        }
        // Try Last.fm
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
                for (tag in tags) {
                    val genreFromTag = GenresUtils.normalizeGenre(tag.name)
                    if (genreFromTag != null) {
                        foundGenre = genreFromTag
                        break
                    }
                }
                return MediaInfoModel(
                    coverUrl = lastFmData.track.album.image.getOrNull(3)?.text,
                    album = lastFmData.track.album.title,
                    track = lastFmData.track.name,
                    artist = lastFmData.track.artist.name,
                    genre = foundGenre,
                    trackUrls = listOf(StreamingLink(StreamingPlatform.LAST_FM, lastFmData.track.url)),
                    trackPreviewUrl = null
                )
            }
        } catch (e: Exception) {
            errors.add(e.message ?: "Last.fm error")
        }
        // Flexible iTunes search fallback
        try {
            val veryCleanTrack = cleanedTrack.split("-")[0].trim()
            val query = listOf(
                "term" to "$veryCleanTrack ${cleanedArtist.split("&")[0].trim()}",
                "entity" to "song",
                "limit" to "5"
            ).formUrlEncode()
            val iTunesResults = fetchItunesApi(query)
            if (iTunesResults.isNotEmpty()) {
                val bestMatch = findBestTrackMatch(iTunesResults, veryCleanTrack, cleanedArtist.split("&")[0].trim())
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
            }
        } catch (e: Exception) {
            errors.add(e.message ?: "iTunes fallback error")
        }
        throw Exception("Unable to find information for '$track' by '$artist'. Details: ${errors.joinToString("; ")}")
    }

    suspend fun getTrackStreamingLinks(url: String, track: String, artist: String): List<StreamingLink> {
        // Odesli
        try {
            val odesliData = fetchOdesliApi(url)
            val odesliLinks = odesliData.linksByPlatform.map { (key, value) ->
                StreamingLink(StreamingPlatformUtils.fromKey(key), value.url)
            }
            return StreamingPlatformUtils.processLinksWithPrioritization(odesliLinks, true)
        } catch (_: Exception) {
        }
        // MusicBrainz fallback
        try {
            var musicBrainzData = fetchMusicBrainzApi("", "recording:\"$track\" AND artist:\"$artist\"")
            val recordings = musicBrainzData["recordings"] as? List<Map<String, Any>> ?: emptyList()
            val trackId = recordings.getOrNull(0)?.get("id") as? String ?: return emptyList()
            musicBrainzData = fetchMusicBrainzApi("/$trackId", "inc=url-rels")
            val relations = musicBrainzData["relations"] as? List<Map<String, Any>> ?: emptyList()
            val musicBrainzLinks = relations.filter { it["type"] == "free streaming" }.map {
                StreamingLink(
                    StreamingPlatformUtils.fromUrl(it["url.resource"] as String),
                    it["url.resource"] as String
                )
            }
            return StreamingPlatformUtils.processLinksWithPrioritization(musicBrainzLinks, false)
        } catch (_: Exception) {
        }
        throw Exception("No streaming links found for track")
    }

    private suspend fun fetchItunesApi(query: String): List<ITunesResponse> {
        val response: HttpResponse = client.get("$ITUNES_API_URL?$query")
        if (!response.status.isSuccess()) throw Exception("iTunes API error: ${response.status.description}")
        val data = Json.decodeFromString<Map<String, Any>>(response.bodyAsText())
        val results = data["results"] as? List<Map<String, Any>> ?: return emptyList()
        return results.map { Json.decodeFromString<ITunesResponse>(Json.encodeToString(it)) }
    }

    private suspend fun fetchOdesliApi(url: String): OdesliResponse {
        val response: HttpResponse = client.get("$ODESLI_API_URL?url=$url")
        if (!response.status.isSuccess()) throw Exception("Odesli API error: ${response.status.description}")
        return Json.decodeFromString(response.bodyAsText())
    }

    private suspend fun fetchLastfmApi(query: String): LastFMResponse {
        val response: HttpResponse = client.get("$LASTFM_API_URL?$query")
        if (!response.status.isSuccess()) throw Exception("Last.fm API error: ${response.status.description}")
        val data = Json.decodeFromString<Map<String, Any>>(response.bodyAsText())
        if (data["message"] != null) throw Exception(data["message"].toString())
        return Json.decodeFromString(response.bodyAsText())
    }

    private suspend fun fetchMusicBrainzApi(url: String, query: String): Map<String, Any> {
        val response: HttpResponse = client.get("$MUSICBRAINZ_API_URL$url?$query&fmt=json")
        if (!response.status.isSuccess()) throw Exception("MusicBrainz API error: ${response.status.description}")
        return Json.decodeFromString(response.bodyAsText())
    }

    // Spotify API and token management not implemented here (requires OAuth and secure storage)
}
