@file:UseSerializers(UUIDSerializer::class, LocalDateTimeSerializer::class)

package org.bscm.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.bscm.serialization.LocalDateTimeSerializer
import org.bscm.serialization.UUIDSerializer
import java.time.LocalDateTime
import java.util.*

@Serializable
data class User(
    val id: UUID,
    val username: String,
    val email: String,
    val imageUrl: String?,
    val discordId: String,
    val createdAt: LocalDateTime,
    val accounts: List<Account>? = null,
    val collections: List<Collection>? = null,
)