package org.bscm.models.tables

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.bscm.models.KnownIssue
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.json.jsonb
import java.time.LocalDate

object VersionTable : IntIdTable("version") {
    val chartId = reference("chart_id", ChartTable, onDelete = ReferenceOption.CASCADE)
    val duration = float("duration")
    val notesAmount = integer("notes_amount")
    val effectsAmount = integer("effects_amount")
    val bpm = integer("bpm")
    val chartUrl = varchar("chart_url", 255)
    val downloadsAmount = integer("downloads_amount").default(0)
    val knownIssues = jsonb("known_issues", Json {
        ignoreUnknownKeys = true
    }, ListSerializer(KnownIssue.serializer())).default(emptyList())
    val publishedAt = date("published_at").clientDefault { LocalDate.now() }
}