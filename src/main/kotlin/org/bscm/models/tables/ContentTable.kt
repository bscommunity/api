package org.bscm.models.tables

import org.bscm.models.enums.ContentType
import org.jetbrains.exposed.dao.id.ULongIdTable
import org.jetbrains.exposed.sql.javatime.datetime

object ContentTable : ULongIdTable("contents") {
    val type = enumerationByName("type", 20, ContentType::class)
    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")

    init {
        index(false, type)
    }
}