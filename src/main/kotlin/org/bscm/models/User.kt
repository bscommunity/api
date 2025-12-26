@file:UseSerializers(UUIDSerializer::class, LocalDateTimeSerializer::class)

package org.bscm.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.bscm.models.enums.UserRole
import org.bscm.serialization.LocalDateTimeSerializer
import org.bscm.serialization.UUIDSerializer
import java.time.LocalDateTime
import java.util.*

@Serializable
data class User(
    val id: UUID,
    val username: String,
    val email: String,
    val imageUrl: String?,
    val bannerUrl: String?,
    val avatarUrl: String?,
    val accentColor: Int?,
    val bio: String?,
    val isPublic: Boolean,

    val role: UserRole,

    val isVerified: Boolean,
    val verifiedAt: LocalDateTime?,

    val discordId: String,
    val createdAt: LocalDateTime,

    val followerCount: Int,
    val followingCount: Int,

    val badges: List<Badge>? = null,
)