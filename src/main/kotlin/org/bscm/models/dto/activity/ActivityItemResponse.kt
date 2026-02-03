@file:UseSerializers(UUIDSerializer::class, LocalDateTimeSerializer::class)

package org.bscm.models.dto.activity

import kotlinx.serialization.UseSerializers
import org.bscm.models.enums.ActivityType
import org.bscm.serialization.LocalDateTimeSerializer
import org.bscm.serialization.UUIDSerializer
import java.time.LocalDateTime
import java.util.*

sealed class ActivityItemResponse {
    abstract val id: UUID
    abstract val type: ActivityType
    abstract val createdAt: LocalDateTime
}
