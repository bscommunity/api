@file:UseSerializers(LocalDateTimeSerializer::class, UUIDSerializer::class)

package org.bscm.models

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.bscm.serialization.LocalDateTimeSerializer
import org.bscm.serialization.UUIDSerializer
import java.util.*

@Serializable
data class Changelog(
    val id: UUID,
    val chartId: String,
    val title: String,
    val description: String? = null,
    val createdAt: LocalDateTime,
)
