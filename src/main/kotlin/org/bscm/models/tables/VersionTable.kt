package org.bscm.models.tables

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.bscm.models.KnownIssue
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.json.jsonb

object VersionTable : IntIdTable("version") {
    val chartId = reference("chart_id", ChartTable, onDelete = ReferenceOption.CASCADE)
    val index = integer("index").autoIncrement()
    val duration = float("duration")
    val notesAmount = integer("notes_amount")
    val effectsAmount = integer("effects_amount")
    val bpm = integer("bpm")
    val chartUrl = varchar("chart_url", 255)
    val downloadsAmount = integer("downloads_amount").default(0)
    val knownIssues = jsonb("known_issues", Json {
        ignoreUnknownKeys = true
    }, ListSerializer(KnownIssue.serializer())).default(emptyList())
    val publishedAt = datetime("published_at").defaultExpression(CurrentDateTime)
}