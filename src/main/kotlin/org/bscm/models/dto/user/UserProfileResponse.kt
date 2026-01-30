@file:UseSerializers(UUIDSerializer::class, LocalDateTimeSerializer::class)

package org.bscm.models.dto.user

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.bscm.models.Badge
import org.bscm.serialization.LocalDateTimeSerializer
import org.bscm.serialization.UUIDSerializer

@Serializable
data class UserProfileResponse(
    val user: SimplifiedUser,
    val badges: List<Badge>? = null,
    val followerCount: Int,
    val followingCount: Int,
    val isPublic: Boolean,
    val isVerified: Boolean,
    val counts: UserProfileCounts
)

