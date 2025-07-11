package org.bscm.models.dto.account

import kotlinx.serialization.Serializable

@Serializable
data class SimplifiedAccount(
    val provider: String,
)