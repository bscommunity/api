@file:UseSerializers(UUIDSerializer::class, LocalDateSerializer::class)

package org.bscm.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.bscm.models.dto.user.SimplifiedUser
import org.bscm.models.enums.ContributorRole
import org.bscm.serialization.LocalDateSerializer
import org.bscm.serialization.UUIDSerializer
import java.time.LocalDate

@Serializable
data class Contributor(
    val user: SimplifiedUser,
    val chartId: String,
    val roles: List<ContributorRole>,
    val joinedAt: LocalDate
)