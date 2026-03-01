package org.bscm.models.dto.user

import kotlinx.serialization.Serializable

@Serializable
data class UserProfileCounts(
    val library: Triple<Int, Int, Int>? = null,
    val likes: Triple<Int, Int, Int>? = null,
    val bookmarks: Triple<Int, Int, Int>? = null,
    val collections: Int? = null,
    val followers: Int? = null,
    val following: Int? = null
)