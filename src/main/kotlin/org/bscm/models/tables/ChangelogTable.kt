package org.bscm.models.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.datetime

object ChangelogTable : UUIDTable("changelogs") {
    val chartId = reference("chart_id", CatalogItemTable, onDelete = ReferenceOption.CASCADE)
    val title = varchar("title", 255)
    val description = varchar("description", 1000).nullable()
    val createdAt = datetime("created_at")
}
