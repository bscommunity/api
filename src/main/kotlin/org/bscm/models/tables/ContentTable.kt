package org.bscm.models.tables

import org.bscm.models.enums.ContentType
import org.bscm.utils.NanoIdUtils
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object ContentTable : IdTable<String>("contents") {
    override val id = varchar("id", 16)
        .clientDefault { NanoIdUtils.generateOptimized(10, "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ", 63, 16) }
        .entityId()
        .uniqueIndex()
    val type = enumerationByName("type", 20, ContentType::class)
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }

    init {
        index(false, type)
    }
}