package org.bscm.models.dto.user

import kotlinx.serialization.Serializable
import org.bscm.serialization.LocalDateTimeSerializer
import org.bscm.serialization.UUIDSerializer
import java.time.LocalDateTime
import java.util.*

@Serializable
data class SimplifiedUser(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val username: String,
    val imageUrl: String? = null,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val isVerified: Boolean,
    val isPublic: Boolean,
    val followerCount: Int,
    val followingCount: Int,
    @Serializable (with = LocalDateTimeSerializer::class)
    val createdAt: LocalDateTime
)