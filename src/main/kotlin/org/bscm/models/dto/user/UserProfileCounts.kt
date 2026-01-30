package org.bscm.models.dto.user

import kotlinx.serialization.Serializable

@Serializable
data class UserProfileCounts(
    val charts: Int,
    val likes: Int,
    val bookmarks: Int,
    val collections: Int
)