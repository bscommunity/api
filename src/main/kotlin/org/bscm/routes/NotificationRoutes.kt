package org.bscm.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.bscm.models.dto.notification.DeleteAllResponse
import org.bscm.models.dto.notification.NotificationsResponse
import org.bscm.models.dto.notification.UnreadCountResponse
import org.bscm.services.NotificationService
import org.bscm.utils.getPagination
import org.bscm.utils.getUserId

fun Route.notificationRoutes(notificationService: NotificationService) {
    route("/me/notifications") {
        authenticate("auth-bearer") {
            get {
                val userId = call.getUserId()
                val (limit, offset) = call.getPagination(coerceLimit = 50, defaultOffset = 0)

                val notifications = notificationService.getNotifications(userId, limit ?: 20, offset ?: 0)
                val unreadCount = notificationService.getUnreadCount(userId)

                call.respond(NotificationsResponse(items = notifications, unreadCount = unreadCount))
            }

            get("/unread-count") {
                val userId = call.getUserId()
                val count = notificationService.getUnreadCount(userId)
                call.respond(UnreadCountResponse(unreadCount = count))
            }

            delete("/{id}") {
                val userId = call.getUserId()
                val id = call.parameters["id"]?.toLongOrNull()
                    ?: throw IllegalArgumentException("Invalid notification ID")

                val deleted = notificationService.deleteNotification(userId, id)
                if (deleted) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Notification not found"))
                }
            }

            post("/read-all") {
                val userId = call.getUserId()
                val deleted = notificationService.deleteAllNotifications(userId)
                call.respond(DeleteAllResponse(deleted = deleted))
            }
        }
    }
}
