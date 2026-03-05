package org.bscm.models.tables

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.bscm.models.Changelog
import org.bscm.models.enums.Difficulty
import org.jetbrains.exposed.dao.id.ULongIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.json.jsonb

object VersionTable : ULongIdTable("versions") {
    val chartId = reference("chart_id", ChartTable, onDelete = ReferenceOption.CASCADE)

    val duration = float("duration")
    val notesAmount = integer("notes_amount")
    val effectsAmount = integer("effects_amount")
    val bpm = integer("bpm")
    val difficulty = enumerationByName("difficulty", 10, Difficulty::class)
    val isDeluxe = bool("is_deluxe").default(false)
    val isExplicit = bool("is_explicit").default(false)
    val downloadsAmount = integer("downloads_amount").default(0)

    val bundleUrl = varchar("bundle_url", 255)
    val previewUrl = varchar("preview_url", 100).nullable()

    val changelog = jsonb(
        "changelog",
        Json { ignoreUnknownKeys = true },
        ListSerializer(Changelog.serializer())
    ).default(emptyList())

    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)

    init {
        // Unique index to ensure only one version per chart at a given time
        index(true, chartId, createdAt)
    }
}
