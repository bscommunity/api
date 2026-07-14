package org.bscm.models.tables

import org.bscm.models.enums.ActivityType
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.javatime.datetime

object UserActivityTable : UUIDTable("user_activity") {
    val userId = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val type = enumerationByName("type", 30, ActivityType::class)
    val targetId = varchar("target_id", 64) // content, chart, user, etc
    val createdAt = datetime("created_at")

    init {
        index(false, userId, createdAt)
    }
}