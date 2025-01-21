@file:UseSerializers(UUIDSerializer::class, LocalDateSerializer::class)

package org.bscm.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.bscm.models.enums.ContributorRole
import org.bscm.serialization.LocalDateSerializer
import org.bscm.serialization.UUIDSerializer
import java.time.LocalDate
import java.util.*

@Serializable
data class Contributor(
    val userId: UUID,
    val chartId: UUID,
    val role: ContributorRole,
    val joinedAt: LocalDate
)