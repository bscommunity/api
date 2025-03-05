package org.bscm.models.dto.user

import kotlinx.serialization.Serializable

@Serializable
data class SimplifiedUser(
    val id: String,
    val username: String,
    val imageUrl: String?,
)