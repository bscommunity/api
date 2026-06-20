@file:UseSerializers(LocalDateTimeSerializer::class)

package org.bscm.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.bscm.models.dto.user.SimplifiedUser
import org.bscm.models.enums.ContributorRole
import org.bscm.serialization.LocalDateTimeSerializer
import java.time.LocalDateTime

@Serializable
data class Contributor(
    val user: SimplifiedUser,
    val catalogItemId: String,
    val note: String? = null,
    val role: ContributorRole,
    val joinedAt: LocalDateTime
)