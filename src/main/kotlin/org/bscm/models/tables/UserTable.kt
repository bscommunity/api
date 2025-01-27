package org.bscm.models.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.datetime

object UserTable : UUIDTable("user") {
    val username = varchar("username", 255).uniqueIndex()
    val email = varchar("email", 255).uniqueIndex()
    val imageUrl = varchar("image_url", 255).nullable()
    val discordId = varchar("discord_id", 255).uniqueIndex()
    val createdAt = datetime("created_at")
}