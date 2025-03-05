package org.bscm.models.dto.user

import kotlinx.serialization.Serializable

@Serializable
data class UpdateUserRequest(
    val username: String? = null,
    val email: String? = null,
    val discordId: String? = null,
    val imageUrl: String? = null
)