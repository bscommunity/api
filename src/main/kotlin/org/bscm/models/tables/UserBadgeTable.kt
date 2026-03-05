package org.bscm.models.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object UserBadgeTable : Table("user_badges") {
    val userId = reference("user_id", UserTable).index()
    val badgeId = reference("badge_id", BadgeTable).index()

    val grantedAt = datetime("granted_at")
        .clientDefault { java.time.LocalDateTime.now() }

    val grantedBy = reference("granted_by", UserTable).nullable()

    override val primaryKey = PrimaryKey(userId, badgeId)
}
