package org.bscm.models.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object UserFollowTable : Table("user_follows") {
    val follower = reference("follower_id", UserTable, onDelete = ReferenceOption.CASCADE).index()
    val followed = reference("followed_id", UserTable, onDelete = ReferenceOption.CASCADE).index()

    val createdAt = datetime("created_at")
        .clientDefault { LocalDateTime.now() }

    override val primaryKey = PrimaryKey(follower, followed)
}

