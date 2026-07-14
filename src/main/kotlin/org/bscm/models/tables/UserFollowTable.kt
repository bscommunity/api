package org.bscm.models.tables

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.datetime

object UserFollowTable : Table("user_follows") {
    val follower = reference("follower_id", UserTable, onDelete = ReferenceOption.CASCADE).index()
    val followed = reference("followed_id", UserTable, onDelete = ReferenceOption.CASCADE).index()

    val createdAt = datetime("created_at")
        .clientDefault { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) }

    override val primaryKey = PrimaryKey(follower, followed)
}

