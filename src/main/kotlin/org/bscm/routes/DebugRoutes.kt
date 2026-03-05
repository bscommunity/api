package org.bscm.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.logging.*
import org.bscm.models.interfaces.IChartRepository
import org.bscm.plugins.UnauthorizedException
import org.bscm.services.MediaInfoService
import org.bscm.services.RefreshService
import org.bscm.services.auth.JWTService
import java.util.*

private val log = KtorSimpleLogger("DebugRoutes")

fun Route.debugRoutes(
    mediaInfoService: MediaInfoService,
    refreshService: RefreshService,
    jwtService: JWTService,
    chartRepository: IChartRepository,
) {
    route("/debug") {
        /**
         * Get media information and streaming links for a track.
         *
         * Tag: Debug
         *
         * Query: track [String] Track name (required).
         * Query: artist [String] Artist name (required).
         *
         * Responses:
         *   - 400 Missing required query parameters.
         *   - 500 Failed to fetch media information.
         *   - 200 Media information with streaming links.
         */
        get("/media") {
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

        /**
         * Refresh bundle URLs for all charts latest versions.
         *
         * Tag: Debug
         *
         * Header: Authorization [String] Bearer token for authentication (required).
         *
         * Responses:
         *   - 401 Invalid verification token.
         *   - 500 Failed to refresh bundle URLs.
         *   - 200 Success message with count of refreshed URLs.
         */
        post("/refresh") {
            println(call.request.headers)
            val authHeader = call.request.headers[HttpHeaders.Authorization]
                ?: throw UnauthorizedException("Missing Authorization header")

            val expectedSecret = application.environment.config
                .property("refresh.secret")
                .getString()

            if (authHeader != "Bearer $expectedSecret") {
                throw UnauthorizedException("Invalid refresh token")
            }

            val messages = refreshService.refreshBundleUrls()

            if (messages.isEmpty()) {
                throw IllegalStateException("No bundle URLs returned from Discord — refresh aborted")
            }

            val result = chartRepository.refreshChartsBundles(messages)

            if (!result) {
                throw IllegalStateException("Failed to persist refreshed bundle URLs")
            }

            call.respond(HttpStatusCode.OK, "Successfully refreshed ${messages.size} bundle URLs")
        }

        /*
        * Returns user by Discord id for debugging purposes. Not documented in API spec and should be removed in production.
        *
        * Tag: Debug
        *
        * Body: [String] Discord ID of the user to retrieve (required).
        *
        * Responses:
        *   - 404 User not found for given Discord ID.
        *   - 200 [AuthResponse] User information with new access and refresh tokens.
        * */
        post("/token") {
            val id = call.receive<String>().let { UUID.fromString(it) }
            call.respond(jwtService.generateAccessToken(id))
        }
    }
}
