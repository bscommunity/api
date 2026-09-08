package org.bscm.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.bscm.models.dao.CatalogItemEntity
import org.bscm.models.dto.contributor.CreateContributorRequest
import org.bscm.models.dto.contributor.UpdateContributorRequest
import org.bscm.models.enums.ContributorInvitePolicy
import org.bscm.models.enums.ContributorRole
import org.bscm.models.interfaces.IContributorRepository
import org.bscm.models.interfaces.IUserRepository
import org.bscm.services.NotificationService
import org.bscm.utils.getUserId
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.*

private suspend fun verifyCatalogItemOwner(catalogItemId: String, userId: UUID) {
    val isOwner = suspendTransaction {
        val catalogItem = CatalogItemEntity.findById(catalogItemId)
            ?: throw NotFoundException("Catalog item not found")
        catalogItem.author?.id?.value == userId
    }
    if (!isOwner) {
        throw SecurityException("Only the author can manage contributors")
    }
}

fun Route.contributorRoutes(
    contributorRepository: IContributorRepository,
    notificationService: NotificationService,
    userRepository: IUserRepository
) {
    route("/contributors/{catalogItemId}") {

        get {
            val catalogItemId = call.parameters["catalogItemId"]
                ?: throw BadRequestException("Invalid or missing catalog item ID")

            val contributors = contributorRepository.getContributors(catalogItemId)
            call.respond(contributors)
        }

        authenticate("auth-bearer") {
            post {
                val catalogItemId = call.parameters["catalogItemId"]
                    ?: throw BadRequestException("Invalid or missing catalog item ID")
                val actorId = call.getUserId()

                verifyCatalogItemOwner(catalogItemId, actorId)

                val request = call.receive<CreateContributorRequest>()
                val targetUserIds = request.contributors.map { it.userId }.distinct()

                val policies = userRepository.getContributorInvitePolicies(targetUserIds)
                val blocked = mutableListOf<UUID>()

                for (userId in targetUserIds) {
                    when (policies[userId]) {
                        ContributorInvitePolicy.NOBODY -> blocked.add(userId)
                        ContributorInvitePolicy.FOLLOWING -> {
                            val follows = userRepository.isFollowing(actorId, userId)
                            if (!follows) blocked.add(userId)
                        }
                        ContributorInvitePolicy.EVERYONE, null -> { /* allowed */ }
                    }
                }

                if (blocked.isNotEmpty()) {
                    throw io.ktor.server.plugins.BadRequestException(
                        "Cannot add contributor(s): ${blocked.joinToString()} do not accept contributor invites from you"
                    )
                }

                val contributors = contributorRepository.addContributors(catalogItemId, request.contributors)

                val recipientIds = request.contributors.map { it.userId }.distinct()
                notificationService.notifyContributorAdded(catalogItemId, actorId, recipientIds)

                call.respond(contributors)
            }

            delete("self") {
                val catalogItemId = call.parameters["catalogItemId"]
                    ?: throw BadRequestException("Invalid or missing catalog item ID")
                val userId = call.getUserId()

                val removed = contributorRepository.removeContributor(catalogItemId, userId, null)
                if (removed) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    throw NotFoundException("Contributor not found")
                }
            }

            put("{userId}") {
                val catalogItemId = call.parameters["catalogItemId"]
                    ?: throw BadRequestException("Invalid or missing catalog item ID")
                val actorId = call.getUserId()
                val targetUserId = call.parameters["userId"]?.let { UUID.fromString(it) }
                    ?: throw BadRequestException("Invalid or missing user ID")

                verifyCatalogItemOwner(catalogItemId, actorId)

                val updatedRequest = call.receive<UpdateContributorRequest>()
                val updated = contributorRepository.updateContributorRoles(catalogItemId, targetUserId, updatedRequest.roles)
                call.respond(updated)
            }

            delete("{userId}") {
                val catalogItemId = call.parameters["catalogItemId"]
                    ?: throw BadRequestException("Invalid or missing catalog item ID")
                val actorId = call.getUserId()
                val targetUserId = call.parameters["userId"]?.let { UUID.fromString(it) }
                    ?: throw BadRequestException("Invalid or missing user ID")
                val role = call.request.queryParameters["role"]
                    ?.let { runCatching { ContributorRole.valueOf(it) }.getOrNull() }

                verifyCatalogItemOwner(catalogItemId, actorId)

                val removed = contributorRepository.removeContributor(catalogItemId, targetUserId, role)
                if (removed) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    throw NotFoundException("Content or contributor not found")
                }
            }
        }
    }
}
