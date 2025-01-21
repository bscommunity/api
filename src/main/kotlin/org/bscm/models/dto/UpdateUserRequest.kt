package org.bscm.models.dto

import kotlinx.serialization.Serializable

@Serializable
data class UpdateUserRequest(
    val username: String? = null,
    val email: String? = null,
    val discordId: String? = null,
    val imageUrl: String? = null
)