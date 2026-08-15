@file:UseSerializers(UUIDSerializer::class, LocalDateTimeSerializer::class)

package org.bscm.models.dto.activity

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.bscm.models.Chart
import org.bscm.models.enums.ActivityType
import org.bscm.serialization.LocalDateTimeSerializer
import org.bscm.serialization.UUIDSerializer
import java.util.*

@Serializable
data class ChartActivityItem(
    override val id: UUID,
    override val type: ActivityType,
    override val createdAt: LocalDateTime,
    val chart: Chart
) : ActivityItemResponse()