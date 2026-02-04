package org.bscm.services

import io.klogging.noCoLogger
import org.bscm.clients.*
import org.bscm.models.StreamingLink
import org.bscm.models.enums.PreviewProvider
import org.bscm.models.enums.StreamingPlatform
import org.bscm.services.media.MediaInfoResult
import org.bscm.utils.GenresUtils
import org.bscm.utils.StreamingPlatformUtils

class MediaInfoService(
    private val itunes: ItunesClient,
    private val deezer: DeezerClient,
    private val lastFm: LastFmClient,
    private val odesli: OdesliClient,
    private val musicbrainz: MusicbrainzClient
) {
    private val logger = noCoLogger(MediaInfoService::class)

    data class TrackMatchContext(
        val track: String,
        val artist: String,
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

    suspend fun getMediaInfo(track: String, artist: String): MediaInfoResult {
        val ctx = TrackMatchContext(
            cleanTrackName(track),
            cleanArtistName(artist)
        )

        // 1. iTunes
        itunes
            .search(ctx.track, ctx.artist)
            .bestMatch(ctx)
            ?.let { match ->
                return MediaInfoResult(
                    coverUrl = match.artworkUrl100.replace("100x100", "600x600"),
                    album = match.collectionName,
                    track = match.trackName,
                    artist = match.artistName,
                    genre = GenresUtils.normalizeGenre(match.primaryGenreName),
                    link = StreamingLink(StreamingPlatform.APPLE_MUSIC, match.trackViewUrl),
                    previewProvider = PreviewProvider.ITUNES,
                    previewProviderTrackId = match.trackId.toString(),
                    isExplicit = match.trackExplicitness == "explicit"
                )
            }

        //2. Deezer fallback
        deezer.search(ctx.track, ctx.artist)
            .firstOrNull()
            ?.let { track ->
                return MediaInfoResult(
                    coverUrl = track.album?.cover_big,
                    album = track.album?.title,
                    track = track.title,
                    artist = track.artist?.name ?: ctx.artist,
                    genre = null,
                    link = StreamingLink(StreamingPlatform.DEEZER, track.link),
                    previewProvider = PreviewProvider.DEEZER,
                    previewProviderTrackId = track.id.toString(),
                    isExplicit = track.explicitLyrics == true
                )
            }

        // 3. Last.fm (metadata fallback)
        lastFm.getTrackInfo(ctx.track, ctx.artist)
            ?.let { lf ->
                val genre = lf.toptags?.tag?.firstNotNullOfOrNull { GenresUtils.normalizeGenre(it.name) }

                return MediaInfoResult(
                    coverUrl = lf.album?.image?.getOrNull(3)?.url,
                    album = lf.album?.title,
                    track = lf.name,
                    artist = lf.artist.name,
                    genre = genre,
                    link = StreamingLink(StreamingPlatform.LAST_FM, lf.url),
                    previewProvider = null,
                    previewProviderTrackId = null,
                    isExplicit = false
                )
            }

        throw IllegalStateException(
            "No media info found for '$track' by '$artist'"
        )
    }

    suspend fun getTrackStreamingLinks(url: String, track: String, artist: String): List<StreamingLink> {
        val cleanedTrack = cleanTrackName(track)
        val cleanedArtist = cleanArtistName(artist)

        // Try Odesli first with a music URL query
        try {
            val odesliLinks = odesli.resolve(url)
            if (odesliLinks.isNotEmpty()) {
                logger.info("Raw Odesli links: $odesliLinks")
                return StreamingPlatformUtils.processLinksWithPrioritization(odesliLinks, true)
            }
        } catch (error: Exception) {
            // Odesli failed, continue to MusicBrainz
        }

        // Fallback to MusicBrainz
        try {
            val musicbrainzLinks = musicbrainz.resolve("recording:\"$cleanedTrack\" AND artist:\"$cleanedArtist\"")
            if (musicbrainzLinks.isNotEmpty()) {
                logger.info("Raw MusicBrainz links: $musicbrainzLinks")
                return StreamingPlatformUtils.processLinksWithPrioritization(musicbrainzLinks, false)
            }
        } catch (error: Exception) {
            // MusicBrainz also failed
        }

        return emptyList()
    }
}
