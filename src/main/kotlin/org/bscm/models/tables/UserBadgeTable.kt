package org.bscm.models.tables

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.datetime
import kotlin.time.Clock

object UserBadgeTable : Table("user_badges") {
    val userId = reference("user_id", UserTable).index()
    val badgeId = reference("badge_id", BadgeTable).index()

    val grantedAt = datetime("granted_at")
        .clientDefault { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) }

    val grantedBy = reference("granted_by", UserTable).nullable()

    override val primaryKey = PrimaryKey(userId, badgeId)
}
