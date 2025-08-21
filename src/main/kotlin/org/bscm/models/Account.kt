// @file:UseSerializers(UUIDSerializer::class, LocalDateTimeSerializer::class)

package org.bscm.models

import kotlinx.serialization.Serializable
import org.bscm.serialization.LocalDateTimeSerializer
import org.bscm.serialization.UUIDSerializer
import java.time.LocalDateTime
import java.util.*

@Serializable
data class Account(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val provider: String,
    // val providerAccountId: String,
    val refreshToken: String? = null,
    val accessToken: String? = null,
    @Serializable(with = LocalDateTimeSerializer::class)
    val expiresAt: LocalDateTime? = null,
    val tokenType: String? = null,
    val scope: String? = null,
    val idToken: String? = null,
    val sessionState: String? = null,
)