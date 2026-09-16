package org.bscm.models.dto.user

import kotlinx.serialization.Serializable
import org.bscm.serialization.UUIDSerializer
import java.util.*

@Serializable
data class SimplifiedUser(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val username: String,
    /** Self-hosted CDN URL mirrored from Discord (see `users.avatar_key`), null when never mirrored. */
    val avatarUrl: String?,
    val bannerUrl: String?,
    val bio: String?,
    val accentColor: Int?,
    val isVerified: Boolean,
    val followersCount: Int? = null,
    val followingCount: Int? = null
)