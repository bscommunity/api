package org.bscm.models.tables

import kotlinx.serialization.json.Json
import org.bscm.models.KnownIssue
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.json.jsonb

object VersionTable : IntIdTable("version") {
    val chartId = reference("chart_id", ChartTable, onDelete = ReferenceOption.CASCADE)
    val index = integer("index")
    val chartUrl = varchar("chart_url", 255)
    val downloadsAmount = integer("downloads_amount")
    val knownIssues = jsonb<KnownIssue>("known_issues", Json.Default)
    val publishedAt = date("created_at")
}