package org.bscm.models.dto.user

import kotlinx.serialization.Serializable
import org.bscm.serialization.UUIDSerializer
import java.util.*

@Serializable
data class SimplifiedUser(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val username: String,
    val avatarUrl: String?,
    val bannerUrl: String?,
    val bio: String?,
    val accentColor: Int?,
    val isVerified: Boolean,
    val followersCount: Int? = null,
    val followingCount: Int? = null
)