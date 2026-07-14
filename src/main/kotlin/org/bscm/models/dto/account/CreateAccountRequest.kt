package org.bscm.models.dto.account

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import org.bscm.serialization.LocalDateTimeSerializer

@Serializable
data class CreateAccountRequest(
    val provider: String,
    // val providerAccountId: String,
    val refreshToken: String? = null,
    val accessToken: String? = null,
    @Serializable(with = LocalDateTimeSerializer::class)
    val expiresAt: LocalDateTime? = null,
    val tokenType: String? = null,
    val scope: String? = null,
)