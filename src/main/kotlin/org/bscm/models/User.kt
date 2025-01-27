// @file:UseSerializers(UUIDSerializer::class, LocalDateSerializer::class)

package org.bscm.models

import kotlinx.serialization.Serializable
import org.bscm.models.dto.CreateUserRequest
import org.bscm.serialization.LocalDateTimeSerializer
import org.bscm.serialization.UUIDSerializer
import java.time.LocalDateTime
import java.util.*

@Serializable
data class User(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val username: String,
    val email: String,
    val imageUrl: String?,
    val discordId: String,
    @Serializable(with = LocalDateTimeSerializer::class)
    val createdAt: LocalDateTime
) {
    companion object {
        fun create(request: CreateUserRequest): User {
            return User(
                id = UUID.randomUUID(), // Auto-generate UUID
                username = request.username,
                email = request.email,
                imageUrl = request.imageUrl,
                discordId = request.discordId,
                createdAt = LocalDateTime.now() // Auto-generate current date
            )
        }
    }
}