package org.bscm.models.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateUserRequest(
    val username: String,
    val email: String,
    val discordId: String,
    val imageUrl: String?
)