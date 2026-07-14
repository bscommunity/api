package org.bscm.models.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.javatime.datetime

object ChangelogTable : UUIDTable("changelogs") {
    val chartId = reference("chart_id", ChartTable, onDelete = ReferenceOption.CASCADE)
    val title = varchar("title", 255)
    val description = varchar("description", 1000).nullable()
    val createdAt = datetime("created_at")
}
