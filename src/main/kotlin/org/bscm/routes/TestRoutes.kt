package org.bscm.routes

import io.klogging.logger
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.bscm.services.MediaInfoService

private val log = logger("TestRoutes")

fun Route.testRoutes(mediaInfoService: MediaInfoService) {
    route("/test") {
        // Add an issue to a chart
        get("/media-info") {
            val trackName = call.request.queryParameters["track"] ?: return@get call.respond(
                "Missing 'track' query parameter"
            )

            val artistName = call.request.queryParameters["artist"] ?: return@get call.respond(
                "Missing 'artist' query parameter"
            )

            val cleanedTrackName = mediaInfoService.cleanTrackName(trackName)
            val cleanedArtistName = mediaInfoService.cleanArtistName(artistName)

            log.info("Fetching media info for track: '$cleanedTrackName', artist: '$cleanedArtistName'")

            val mediaInfo = try { mediaInfoService.getMediaInfo(cleanedTrackName, cleanedArtistName) } catch (e: Exception) {
                log.info("Media info fetch failed: ${e.message}")
                throw e
            }

            // Streaming links resolution
            val streamingLinks = try {
                mediaInfoService.getTrackStreamingLinks(mediaInfo.link.url, cleanedTrackName, cleanedArtistName)
            } catch (_: Exception) {
                listOf(mediaInfo.link)
            }

            log.info("Resolved streaming links: $streamingLinks")

            call.respond(mediaInfo)
        }
    }
}
