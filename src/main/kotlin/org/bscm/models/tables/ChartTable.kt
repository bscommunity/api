package org.bscm.models.tables

import org.bscm.models.enums.Difficulty
import org.jetbrains.exposed.dao.id.ULongIdTable
import org.jetbrains.exposed.sql.ReferenceOption

// CatalogItemTable brings contentId, contentId, coverUrl, isPublic, isFeatured, downloadsSum, latestPublishedAt, author
object ChartTable : ULongIdTable("charts") {
    val catalogId =
        reference(
            "catalog_item_id",
            CatalogItemTable,
            onDelete = ReferenceOption.CASCADE
        ).uniqueIndex()

    val versionsCount =
        integer("versions_count")
            .default(0)

    val latestVersionId =
        reference(
            "latest_version_id",
            VersionTable,
            onDelete = ReferenceOption.SET_NULL
        ).nullable()

    val difficulty = enumerationByName("difficulty", 10, Difficulty::class)
    val notesAmount = integer("notes_amount")
    val effectsAmount = integer("effects_amount")

    val isDeluxe = bool("is_deluxe").default(false)
    val isExplicit = bool("is_explicit").default(false)
}