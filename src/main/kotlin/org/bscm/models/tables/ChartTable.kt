package org.bscm.models.tables

import org.bscm.models.enums.Difficulty
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable

object ChartTable : IdTable<String>("charts") {
    override val id: Column<EntityID<String>> = varchar("id", 10).entityId()

    val trackId = reference("track_id", TrackTable, onDelete = ReferenceOption.RESTRICT)

    val difficulty = enumerationByName("difficulty", 10, Difficulty::class)
    val notesAmount = integer("notes_amount")
    val effectsAmount = integer("effects_amount")

    val isDeluxe = bool("is_deluxe").default(false)
    val isExplicit = bool("is_explicit").default(false)

    override val primaryKey = PrimaryKey(id)
}
