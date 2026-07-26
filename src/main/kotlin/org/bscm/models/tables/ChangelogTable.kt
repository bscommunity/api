package org.bscm.models.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.datetime.datetime

object ChangelogTable : UUIDTable("changelogs") {
    val chartId = reference("chart_id", ChartTable, onDelete = ReferenceOption.CASCADE)
    val description = varchar("description", 1000)
    val createdAt = datetime("created_at")
}
