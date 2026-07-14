package org.bscm.models.tables

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.datetime.datetime

object BadgeTable : UUIDTable("badges") {
    val name = varchar("name", 255).uniqueIndex()
    val description = text("description").nullable()
    val criteria = text("criteria").nullable()
    val createdAt = datetime("created_at").clientDefault { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) }
}