package org.bscm.models.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.date

object UserTable : UUIDTable("user") {
    val username = varchar("username", 255).uniqueIndex()
    val email = varchar("email", 255).uniqueIndex().nullable()
    val imageUrl = varchar("image_url", 255).nullable()
    val createdAt = date("created_at")
}