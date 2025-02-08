@file:UseSerializers(UUIDSerializer::class, LocalDateSerializer::class)

package org.bscm.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.bscm.models.dto.SimplifiedUser
import org.bscm.models.enums.ContributorRole
import org.bscm.serialization.LocalDateSerializer
import org.bscm.serialization.UUIDSerializer
import java.time.LocalDate
import java.util.*

@Serializable
data class Contributor(
    val user: SimplifiedUser,
    val chartId: UUID,
    val roles: List<ContributorRole>,
    val joinedAt: LocalDate
)