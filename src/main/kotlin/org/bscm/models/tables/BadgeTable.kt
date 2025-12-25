package org.bscm.models.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.datetime

object BadgeTable : UUIDTable("badges") {
    val name = varchar("name", 255).uniqueIndex()
    val description = text("description").nullable()
    val criteria = text("criteria").nullable()
    val createdAt = datetime("created_at").clientDefault { java.time.LocalDateTime.now() }
}