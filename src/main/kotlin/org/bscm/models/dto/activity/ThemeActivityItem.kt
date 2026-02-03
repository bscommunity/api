@file:UseSerializers(UUIDSerializer::class, LocalDateTimeSerializer::class)

package org.bscm.models.dto.activity

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.bscm.models.Theme
import org.bscm.models.enums.ActivityType
import org.bscm.serialization.LocalDateTimeSerializer
import org.bscm.serialization.UUIDSerializer
import java.time.LocalDateTime
import java.util.*

@Serializable
data class ThemeActivityItem(
    override val id: UUID,
    override val type: ActivityType,
    override val createdAt: LocalDateTime,
    val theme: Theme
) : ActivityItemResponse()