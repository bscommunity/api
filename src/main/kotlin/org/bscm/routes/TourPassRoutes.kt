package org.bscm.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.bscm.models.repository.ITourPassRepository
import org.bscm.plugins.UnauthorizedException
import java.util.*

data class CreateTourPassRequest(
    val name: String,
    val artist: String?,
    val coverUrl: String
)

data class UpdateTourPassRequest(
    val name: String?,
    val artist: String?,
    val coverUrl: String?
)

fun Route.tourPassRoutes(tourPassRepository: ITourPassRepository) {
    route("/tourpasses") {
        authenticate("auth-bearer", optional = true) {
            rateLimit(RateLimitName("unrestricted")) {
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

                get("/{id}") {
                    val id = call.parameters["id"]?.toULongOrNull()
                        ?: throw BadRequestException("Invalid or missing ID parameter")

                    val tourPass = tourPassRepository.getTourPassById(id)
                        ?: throw NotFoundException("TourPass not found")

                    call.respond(tourPass)
                }

                get("/app/{contentId}") {
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

                put("/{id}") {
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

                delete("/{id}") {
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

                // Add chart to tour pass
                post("/{id}/charts/{chartId}") {
                    val principal = call.principal<JWTPrincipal>()
                        ?: throw UnauthorizedException("Authentication required")

                    val tourPassId = call.parameters["id"]?.toULongOrNull()
                        ?: throw BadRequestException("Invalid or missing tour pass ID parameter")

                    val chartId = call.parameters["chartId"]?.toULongOrNull()
                        ?: throw BadRequestException("Invalid or missing chart ID parameter")

                    val success = tourPassRepository.addChartToTourPass(tourPassId, chartId)
                    if (success) {
                        call.respond(HttpStatusCode.OK, mapOf("message" to "Chart added to tour pass successfully"))
                    } else {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Chart already in tour pass or tour pass not found"))
                    }
                }

                // Remove chart from tour pass
                delete("/{id}/charts/{chartId}") {
                    val principal = call.principal<JWTPrincipal>()
                        ?: throw UnauthorizedException("Authentication required")

                    val tourPassId = call.parameters["id"]?.toULongOrNull()
                        ?: throw BadRequestException("Invalid or missing tour pass ID parameter")

                    val chartId = call.parameters["chartId"]?.toULongOrNull()
                        ?: throw BadRequestException("Invalid or missing chart ID parameter")

                    val success = tourPassRepository.removeChartFromTourPass(tourPassId, chartId)
                    if (success) {
                        call.respond(HttpStatusCode.OK, mapOf("message" to "Chart removed from tour pass successfully"))
                    } else {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Chart not found in tour pass"))
                    }
                }
            }
        }
    }
}
