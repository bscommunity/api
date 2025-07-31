package org.bscm.models.tables

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.bscm.models.KnownIssue
import org.bscm.models.enums.Difficulty
import org.jetbrains.exposed.dao.id.ULongIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.json.jsonb
import java.time.LocalDate

object VersionTable : ULongIdTable("versions") {
    val chartId = reference("chart_id", ChartTable, onDelete = ReferenceOption.CASCADE)

    val index = integer("index").default(1)
    val duration = float("duration")
    val notesAmount = integer("notes_amount")
    val effectsAmount = integer("effects_amount")
    val bpm = integer("bpm")
    val difficulty = enumerationByName("difficulty", 10, Difficulty::class)
    val isDeluxe = bool("is_deluxe").default(false)
    val isExplicit = bool("is_explicit").default(false)

    val bundleUrl = varchar("bundle_url", 255)
    val previewUrl = varchar("preview_url", 100).nullable()

    val downloadsAmount = integer("downloads_amount").default(0)
    val knownIssues = jsonb(
        "known_issues",
        Json { ignoreUnknownKeys = true },
        ListSerializer(KnownIssue.serializer())
    ).default(emptyList())
    val publishedAt = date("published_at").clientDefault { LocalDate.now() }

    init {
        index("idx_version_downloads_amount", false, downloadsAmount)
    }
}
