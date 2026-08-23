package org.bscm.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.logging.*
import org.bscm.models.dto.tourpass.CreateTourPassRequest
import org.bscm.models.dto.tourpass.UpdateTourPassRequest
import org.bscm.models.dto.user.PagedResponse
import org.bscm.models.interfaces.ITourPassRepository
import org.bscm.models.interfaces.IUserRepository
import org.bscm.plugins.UnauthorizedException
import org.bscm.services.publish.TourPassPublishService
import org.bscm.services.track.clients.jsonClient
import java.util.*

private val logger = KtorSimpleLogger("TourPassRoutes")

fun Route.tourPassRoutes(
    tourPassPublishService: TourPassPublishService,
    tourPassRepository: ITourPassRepository,
    userRepository: IUserRepository,
) {

    route("/tourpasses") {

        // Public browse endpoint
        authenticate("auth-public") {
            rateLimit(RateLimitName("restricted")) {
                get {
                    val search = call.request.queryParameters["query"]
                        ?.takeIf { it.isNotBlank() }
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull()
                    val offset = call.request.queryParameters["offset"]?.toIntOrNull()
                    val count = call.request.queryParameters["count"]?.toBoolean() ?: false

                    val tourPasses = tourPassRepository.getTourPasses(
                        search = search,
                        limit = limit,
                        offset = offset
                    )

                    val total = if (count) {
                        tourPassRepository.countTourPasses(search = search)
                    } else null

                    call.respond(PagedResponse(items = tourPasses, total = total))
                }
            }
        }

        // Authenticated routes
        authenticate("auth-bearer") {
            rateLimit(RateLimitName("restricted")) {

                get("{id}") {
                    val id = call.parameters["id"]
                        ?: throw BadRequestException("Invalid or missing tour pass ID")

                    val userId = call.principal<JWTPrincipal>()
                        ?.subject
                        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: throw UnauthorizedException("User unauthorized")

                    val tourPass = tourPassRepository.getTourPassById(id, userId)
                        ?: throw NotFoundException("Tour pass not found")

                    if (tourPass.visibility != org.bscm.models.enums.Visibility.PUBLIC) {
                        val isAuthor = tourPass.authorId == userId
                        if (!isAuthor) throw NotFoundException("Tour pass not found")
                    }

                    call.respond(tourPass)
                }

                post {
                    val userId = call.principal<JWTPrincipal>()
                        ?.subject
                        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: throw UnauthorizedException("User unauthorized")

                    val user = userRepository.getUserById(userId)
                        ?: throw UnauthorizedException("User not found")

                    val multipart = call.parseMultipartPayload(
                        acceptedFormFields = setOf("tourpass"),
                        fileAliases = mapOf("cover" to "cover"),
                    ) ?: throw BadRequestException("multipart/form-data is required")

                    val tourpassJson = multipart.fields["tourpass"]
                        ?: throw BadRequestException("tourpass JSON field is required")

                    val request = runCatching {
                        jsonClient.decodeFromString<CreateTourPassRequest>(tourpassJson)
                    }.getOrElse {
                        throw BadRequestException("Invalid tourpass JSON: ${it.message}")
                    }

                    val coverBytes = multipart.files["cover"]?.bytes
                    val coverContentType = multipart.files["cover"]?.contentType

                    val tourPass = tourPassPublishService.createAndPublish(
                        uploader = user,
                        request = request,
                        coverBytes = coverBytes,
                        coverContentType = coverContentType,
                    )

                    logger.info("TourPass ${tourPass.id} created by user $userId")
                    call.respond(HttpStatusCode.Created, tourPass)
                }

                put("{id}") {
                    val id = call.parameters["id"]
                        ?: throw BadRequestException("Invalid or missing tour pass ID")

                    val userId = call.principal<JWTPrincipal>()
                        ?.subject
                        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: throw UnauthorizedException("User unauthorized")

                    userRepository.getUserById(userId)
                        ?: throw UnauthorizedException("User not found")

                    val multipart = call.parseMultipartPayload(
                        acceptedFormFields = setOf("tourpass"),
                        fileAliases = mapOf("cover" to "cover"),
                    ) ?: throw BadRequestException("multipart/form-data is required")

                    val tourpassJson = multipart.fields["tourpass"]
                        ?: throw BadRequestException("tourpass JSON field is required")

                    val request = runCatching {
                        jsonClient.decodeFromString<UpdateTourPassRequest>(tourpassJson)
                    }.getOrElse {
                        throw BadRequestException("Invalid tourpass JSON: ${it.message}")
                    }

                    val coverBytes = multipart.files["cover"]?.bytes
                    val coverContentType = multipart.files["cover"]?.contentType

                    val tourPass = tourPassPublishService.updateAndPublish(
                        id = id,
                        userId = userId,
                        request = request,
                        coverBytes = coverBytes,
                        coverContentType = coverContentType,
                    )

                    logger.info("TourPass $id updated by user $userId")
                    call.respond(tourPass)
                }

                delete("{id}") {
                    val id = call.parameters["id"]
                        ?: throw BadRequestException("Invalid or missing tour pass ID")

                    val userId = call.principal<JWTPrincipal>()
                        ?.subject
                        ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                        ?: throw UnauthorizedException("User unauthorized")

                    userRepository.getUserById(userId)
                        ?: throw UnauthorizedException("User not found")

                    val deleted = tourPassPublishService.deleteAndCleanup(id, userId)
                    if (!deleted) {
                        throw NotFoundException("Tour pass not found or not owned by user")
                    }

                    logger.info("TourPass $id deleted by user $userId")
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}
