package org.bscm.models.dto.account

import kotlinx.serialization.Serializable
import org.bscm.serialization.LocalDateSerializer
import java.time.LocalDate

@Serializable
data class CreateAccountRequest(
    val provider: String,
    // val providerAccountId: String,
    val refreshToken: String? = null,
    val accessToken: String? = null,
    @Serializable(with = LocalDateSerializer::class)
    val expiresAt: LocalDate? = null,
    val tokenType: String? = null,
    val scope: String? = null,
)