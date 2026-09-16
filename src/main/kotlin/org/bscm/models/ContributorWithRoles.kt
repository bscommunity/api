@file:UseSerializers(LocalDateTimeSerializer::class)

package org.bscm.models

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.bscm.models.dto.user.SimplifiedUser
import org.bscm.models.enums.ContributorRole
import org.bscm.serialization.LocalDateTimeSerializer

/**
 * Client-facing contributor: one entry per user, with all of their roles
 * on the catalog item grouped in [roles].
 *
 * The underlying `contributors` table stores one row per (catalogItem, user, role);
 * repository layers group those rows into this shape before sending to clients.
 * Applies to all content types (charts, tour passes, themes).
 */
@Serializable
data class ContributorWithRoles(
    val user: SimplifiedUser,
    val catalogItemId: String,
    val roles: List<ContributorRole>,
    val note: String? = null,
    val joinedAt: LocalDateTime,
)
