package org.bscm.models.dto.user

import kotlinx.serialization.Serializable

@Serializable
data class CreateUserRequest(
    val username: String,
    val email: String,
    val discordId: String,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val accentColor: Int? = null,
)