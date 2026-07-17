package org.bscm.services.track

import io.ktor.util.logging.*
import kotlinx.serialization.Serializable
import org.bscm.models.StreamingRef
import org.bscm.models.enums.PreviewProvider
import org.bscm.models.enums.StreamingPlatform
import org.bscm.services.track.clients.*
import org.bscm.utils.GenresUtils
import org.bscm.utils.StreamingPlatformUtils

private val logger = KtorSimpleLogger("TrackInfoService")

class TrackInfoService(
    private val itunes: ItunesClient,
    private val deezer: DeezerClient,
    private val lastFm: LastFmClient,
    private val odesli: OdesliClient,
    private val musicbrainz: MusicbrainzClient,
    private val musicLink: MusicLinkClient
) {

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

    suspend fun getTrackInfo(track: String, artist: String): TrackInfoResult {
        val ctx = TrackMatchContext(
            cleanTrackName(track),
            cleanArtistName(artist)
        )

        // 1. iTunes
        itunes
            .search(ctx.track, ctx.artist)
            .bestMatch(ctx)
            ?.let { match ->
                logger.debug { "Best iTunes match for '$track' by '$artist': ${match.trackName} by ${match.artistName}" }
                return TrackInfoResult(
                    coverUrl = match.artworkUrl100.replace("100x100", "600x600"),
                    album = match.collectionName,
                    track = match.trackName,
                    artist = match.artistName,
                    genres = (match.genreNames.takeIf { it.isNotEmpty() }
                        ?: listOf(match.primaryGenreName))
                        .mapNotNull { GenresUtils.normalizeGenre(it) }
                        .distinct(),
                    link = StreamingRef(StreamingPlatform.APPLE_MUSIC, match.trackViewUrl),
                    previewProvider = PreviewProvider.ITUNES,
                    previewProviderTrackId = match.trackId.toString(),
                    isExplicit = match.trackExplicitness == "explicit",
                    isrc = match.isrc
                )
            }

        // 2. Deezer fallback
        deezer.search(ctx.track, ctx.artist)
            .firstOrNull()
            ?.let { track ->
                logger.debug { "Deezer found for '$track' by '$artist'" }
                return TrackInfoResult(
                    coverUrl = track.album?.cover_big,
                    album = track.album?.title,
                    track = track.title,
                    artist = track.artist?.name ?: ctx.artist,
                    genres = emptyList(),
                    link = StreamingRef(StreamingPlatform.DEEZER, track.link),
                    previewProvider = PreviewProvider.DEEZER,
                    previewProviderTrackId = track.id.toString(),
                    isExplicit = track.explicitLyrics == true,
                    isrc = track.isrc
                )
            }

        // 3. Last.fm (metadata fallback)
        lastFm.getTrackInfo(ctx.track, ctx.artist)
            ?.let { lf ->
                val genres = lf.toptags?.tag
                    ?.mapNotNull { GenresUtils.normalizeGenre(it.name) }
                    ?.distinct()
                    .orEmpty()

                logger.debug { "LastFM found for '$track' by '$artist': ${lf.name} by ${lf.artist.name}" }
                return TrackInfoResult(
                    coverUrl = lf.album?.image?.getOrNull(3)?.url,
                    album = lf.album?.title,
                    track = lf.name,
                    artist = lf.artist.name,
                    genres = genres,
                    link = StreamingRef(StreamingPlatform.LAST_FM, lf.url),
                    previewProvider = null,
                    previewProviderTrackId = null,
                    isExplicit = false
                )
            }

        throw IllegalStateException(
            "No media info found for '$track' by '$artist'"
        )
    }

    @Serializable
    data class StreamingLinksResult(
        val links: List<StreamingRef>,
        val isrc: String? = null
    )

    suspend fun getTrackStreamingLinks(url: String, track: String, artist: String, isrc: String? = null, platform: StreamingPlatform? = null): StreamingLinksResult {
        val cleanedTrack = cleanTrackName(track)
        val cleanedArtist = cleanArtistName(artist)

        // 1. MusicLink (HTML scraping + API by ISRC)
        try {
            val musicLinkResult = musicLink.resolve(cleanedArtist, cleanedTrack, isrc)
            if (musicLinkResult.links.isNotEmpty()) {
                logger.info("Raw MusicLink links: ${musicLinkResult.links}")
                return StreamingLinksResult(
                    links = StreamingPlatformUtils.processLinksWithPrioritization(musicLinkResult.links, false),
                    isrc = musicLinkResult.isrc
                )
            }
        } catch (error: Exception) {
            logger.warn("MusicLink failed: ${error.message}")
        }

        // 2. MusicLink by platform URL (fallback when no ISRC)
        if (isrc.isNullOrBlank() && url.isNotBlank() && platform != null) {
            try {
                val platformResult = musicLink.resolveByPlatformUrl(platform, url)
                if (platformResult.links.isNotEmpty()) {
                    logger.info("Raw MusicLink platform URL links: ${platformResult.links}")
                    return StreamingLinksResult(
                        links = StreamingPlatformUtils.processLinksWithPrioritization(platformResult.links, false),
                        isrc = platformResult.isrc
                    )
                }
            } catch (error: Exception) {
                logger.warn("MusicLink platform URL lookup failed: ${error.message}")
            }
        }

        // 3. MusicBrainz
        try {
            val musicbrainzLinks = musicbrainz.resolve("recording:\"$cleanedTrack\" AND artist:\"$cleanedArtist\"")
            if (musicbrainzLinks.isNotEmpty()) {
                logger.info("Raw MusicBrainz links: $musicbrainzLinks")
                return StreamingLinksResult(
                    links = StreamingPlatformUtils.processLinksWithPrioritization(musicbrainzLinks, false)
                )
            }
        } catch (error: Exception) {
            logger.warn("MusicBrainz failed: ${error.message}")
        }

        // 4. Last.fm (metadata fallback + HTML scraping)
        try {
            val lastFmTrack = lastFm.getTrackInfo(cleanedTrack, cleanedArtist)
            if (lastFmTrack != null) {
                val scrapedLinks = lastFm.getTrackStreamingLinks(lastFmTrack.url)
                if (scrapedLinks.isNotEmpty()) {
                    logger.info("Raw Last.fm scraped links: $scrapedLinks")
                    return StreamingLinksResult(
                        links = StreamingPlatformUtils.processLinksWithPrioritization(scrapedLinks, false)
                    )
                }
                val lastFmLink = StreamingRef(StreamingPlatform.LAST_FM, lastFmTrack.url)
                logger.info("Raw Last.fm link (no scraped playlinks): $lastFmLink")
                return StreamingLinksResult(links = listOf(lastFmLink))
            }
        } catch (error: Exception) {
            logger.warn("Last.fm failed: ${error.message}")
        }

        // 5. Odesli (API soon deprecated)
        try {
            val odesliLinks = odesli.resolve(url)
            if (odesliLinks.isNotEmpty()) {
                logger.info("Raw Odesli links: $odesliLinks")
                return StreamingLinksResult(
                    links = StreamingPlatformUtils.processLinksWithPrioritization(odesliLinks, true)
                )
            }
        } catch (error: Exception) {
            logger.warn("Odesli failed: ${error.message}")
        }

        return StreamingLinksResult(emptyList())
    }
}
