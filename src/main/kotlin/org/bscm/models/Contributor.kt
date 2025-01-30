@file:UseSerializers(UUIDSerializer::class, LocalDateTimeSerializer::class)

package org.bscm.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.bscm.models.enums.ContributorRole
import org.bscm.serialization.LocalDateTimeSerializer
import org.bscm.serialization.UUIDSerializer
import java.time.LocalDateTime
import java.util.*

@Serializable
data class Contributor(
    val user: User,
    val chartId: UUID,
    val roles: List<ContributorRole>,
    val joinedAt: LocalDateTime
)