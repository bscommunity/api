package org.bscm.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.bscm.models.interfaces.ITourPassRepository
import org.bscm.plugins.UnauthorizedException
import java.util.*

@kotlinx.serialization.Serializable
data class CreateTourPassRequest(
    val name: String,
    val artist: String?,
    val coverUrl: String
)

@Serializable
data class UpdateTourPassRequest(
    val name: String?,
    val artist: String?,
    val coverUrl: String?
)

fun Route.tourPassRoutes(tourPassRepository: ITourPassRepository) {
    route("/tourpasses") {
        authenticate("auth-bearer", optional = true) {
            rateLimit(RateLimitName("unrestricted")) {
                /**
                 * List tour passes with optional search and filtering.
                 * Tag: TourPasses
                 *
                 * Query: search [String] Optional search string to filter tour passes.
                 * Query: limit [Integer] Optional limit for results.
                 * Query: offset [Integer] Optional pagination offset.
                 * Query: ids [String] Comma-separated tour pass IDs to retrieve.
                 *
                 * Response: 200 application/json List of tour passes.
                 */
                get {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.getClaim("sub")?.asString()?.let { UUID.fromString(it) }

                    val search = call.request.queryParameters["search"]
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull()
                    val offset = call.request.queryParameters["offset"]?.toIntOrNull()
                    val ids = call.request.queryParameters.getAll("ids")

                    val tourPasses = tourPassRepository.getTourPasses(
                        userId = userId,
                        contentIds = ids,
                        search = search,
                        limit = limit,
                        offset = offset
                    )

                    call.respond(tourPasses)
                }

                /**
                 * Get tour pass by ID.
                 * Tag: TourPasses
                 *
                 * Path: id [ULong] Tour pass ID.
                 *
                 * Responses:
                 *   - 400 ID parameter is malformatted or missing.
                 *   - 404 Tour pass not found.
                 *   - 200 Tour pass details.
                 */
                get("/{id}") {
                    val id = call.parameters["id"]?.toULongOrNull()
                        ?: throw BadRequestException("Invalid or missing ID parameter")

                    val tourPass = tourPassRepository.getTourPassById(id)
                        ?: throw NotFoundException("TourPass not found")

                    call.respond(tourPass)
                }

                /**
                 * Get tour pass by content ID.
                 * Tag: TourPasses
                 *
                 * Path: contentId [String] Content ID.
                 *
                 * Responses:
                 *   - 400 Missing contentId parameter.
                 *   - 404 Tour pass not found.
                 *   - 200 Tour pass details.
                 */
                get("/{contentId}") {
                    val contentId = call.parameters["contentId"]
                        ?: throw BadRequestException("Missing contentId parameter")

                    val tourPass = tourPassRepository.getAppTourPassById(contentId)
                        ?: throw NotFoundException("TourPass not found")

                    call.respond(tourPass)
                }
            }
        }

        authenticate("auth-bearer") {
            rateLimit(RateLimitName("restricted")) {
                /**
                 * Create a new tour pass.
                 * Tag: TourPasses
                 *
                 * Security: auth-bearer
                 *
                 * Body: application/json Tour pass name, artist, and cover URL [CreateTourPassRequest].
                 *
                 * Response: 201 application/json Created tour pass.
                 * Response: 400 application/json Authentication required or invalid request.
                 */
                post {
                    val principal = call.principal<JWTPrincipal>()
                        ?: throw UnauthorizedException("Authentication required")
                    val userId = UUID.fromString(principal.payload.getClaim("sub").asString())

                    val request = call.receive<CreateTourPassRequest>()

                    val tourPass = tourPassRepository.createTourPass(
                        userId = userId,
                        name = request.name,
                        artist = request.artist,
                        coverUrl = request.coverUrl
                    )

                    call.respond(HttpStatusCode.Created, tourPass)
                }

                route("/{id}") {
                    /**
                     * Update an existing tour pass.
                     * Tag: TourPasses
                     *
                     * Path: id [ULong] Tour pass ID.
                     * Body: application/json Fields to update [UpdateTourPassRequest].
                     *
                     * Responses:
                     *   - 400 ID parameter is malformatted or missing, or authentication required.
                     *   - 200 Updated tour pass.
                     */
                    put {
                        val principal = call.principal<JWTPrincipal>()
                            ?: throw UnauthorizedException("Authentication required")

                        val id = call.parameters["id"]?.toULongOrNull()
                            ?: throw BadRequestException("Invalid or missing ID parameter")

                        val request = call.receive<UpdateTourPassRequest>()

                        val tourPass = tourPassRepository.updateTourPass(
                            id = id,
                            name = request.name,
                            artist = request.artist,
                            coverUrl = request.coverUrl
                        )

                        call.respond(tourPass)
                    }

                    /**
                     * Delete a tour pass.
                     * Tag: TourPasses
                     *
                     * Path: id [ULong] Tour pass ID.
                     *
                     * Responses:
                     *   - 400 ID parameter is malformatted or missing, or authentication required.
                     *   - 404 Tour pass not found.
                     *   - 204 Tour pass deleted successfully.
                     */
                    delete {
                        val principal = call.principal<JWTPrincipal>()
                            ?: throw UnauthorizedException("Authentication required")

                        val id = call.parameters["id"]?.toULongOrNull()
                            ?: throw BadRequestException("Invalid or missing ID parameter")

                        val success = tourPassRepository.deleteTourPass(id)
                        if (success) {
                            call.respond(HttpStatusCode.NoContent)
                        } else {
                            throw NotFoundException("TourPass not found")
                        }
                    }

                    route("/charts/{chartId}") {

                        /**
                         * Add chart to tour pass.
                         * Tag: TourPasses
                         *
                         * Path: id [ULong] Tour pass ID.
                         * Path: chartId [ULong] Chart ID.
                         *
                         * Responses:
                         *   - 400 Invalid parameters or chart already in tour pass.
                         *   - 401 Authentication required.
                         *   - 200 Success message.
                         */
                        post {
                            val principal = call.principal<JWTPrincipal>()
                                ?: throw UnauthorizedException("Authentication required")

                            val tourPassId = call.parameters["id"]?.toULongOrNull()
                                ?: throw BadRequestException("Invalid or missing tour pass ID parameter")

                            val chartId = call.parameters["chartId"]?.toULongOrNull()
                                ?: throw BadRequestException("Invalid or missing chart ID parameter")

                            val success = tourPassRepository.addChartToTourPass(tourPassId, chartId)
                            if (success) {
                                call.respond(
                                    HttpStatusCode.OK,
                                    mapOf("message" to "Chart added to tour pass successfully")
                                )
                            } else {
                                call.respond(
                                    HttpStatusCode.BadRequest,
                                    mapOf("error" to "Chart already in tour pass or tour pass not found")
                                )
                            }
                        }

                        /**
                         * Remove chart from tour pass.
                         * Tag: TourPasses
                         *
                         * Path: id [ULong] Tour pass ID.
                         * Path: chartId [ULong] Chart ID.
                         *
                         * Responses:
                         *   - 400 Invalid parameters or authentication required.
                         *   - 404 Chart not found in tour pass.
                         *   - 200 Success message.
                         */
                        delete {
                            call.principal<JWTPrincipal>()
                                ?: throw UnauthorizedException("Authentication required")

                            val tourPassId = call.parameters["id"]?.toULongOrNull()
                                ?: throw BadRequestException("Invalid or missing tour pass ID parameter")

                            val chartId = call.parameters["chartId"]?.toULongOrNull()
                                ?: throw BadRequestException("Invalid or missing chart ID parameter")

                            val success = tourPassRepository.removeChartFromTourPass(tourPassId, chartId)
                            if (success) {
                                call.respond(
                                    HttpStatusCode.OK,
                                    mapOf("message" to "Chart removed from tour pass successfully")
                                )
                            } else {
                                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Chart not found in tour pass"))
                            }
                        }
                    }
                }
            }
        }
    }
}
