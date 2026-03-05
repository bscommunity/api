package org.bscm.models.tables

import org.bscm.models.enums.ActivityType
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.datetime

object UserActivityTable : UUIDTable("user_activity") {
    val userId = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val type = enumerationByName("type", 30, ActivityType::class)
    val targetId = varchar("target_id", 64) // content, chart, user, etc
    val createdAt = datetime("created_at")

    init {
        index(false, userId, createdAt)
    }
}