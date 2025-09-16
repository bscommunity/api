@file:UseSerializers(UUIDSerializer::class, LocalDateTimeSerializer::class)

package org.bscm.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.bscm.serialization.LocalDateTimeSerializer
import org.bscm.serialization.UUIDSerializer
import java.time.LocalDateTime

@Serializable
data class Interaction(
    val chart: Chart?,
    // val tourPass: TourPass?,
    // val theme: Theme?,

    val likedAt: LocalDateTime?,
    val favoritedAt: LocalDateTime?,
)