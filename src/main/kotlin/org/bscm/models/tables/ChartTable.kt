package org.bscm.models.tables

import org.bscm.models.enums.Difficulty
import org.jetbrains.exposed.dao.id.ULongIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object ChartTable : ULongIdTable("charts") {
    val catalogItemId =
        reference(
            "catalog_item_id",
            CatalogItemTable,
            onDelete = ReferenceOption.CASCADE
        ).uniqueIndex()

    val trackId =
        reference(
            "track_id",
            TrackTable,
            onDelete = ReferenceOption.CASCADE
        )

    val difficulty = enumerationByName("difficulty", 10, Difficulty::class)
    val notesAmount = integer("notes_amount")
    val effectsAmount = integer("effects_amount")

    val isDeluxe = bool("is_deluxe").default(false)
    val isExplicit = bool("is_explicit").default(false)
}