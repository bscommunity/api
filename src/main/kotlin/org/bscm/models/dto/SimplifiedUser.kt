package org.bscm.models.dto

import kotlinx.serialization.Serializable

@Serializable
data class SimplifiedUser(
    val username: String,
    val imageUrl: String?,
)