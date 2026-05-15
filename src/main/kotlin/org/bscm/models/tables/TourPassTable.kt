package org.bscm.models.tables

import org.jetbrains.exposed.dao.id.ULongIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object TourPassTable : ULongIdTable("tour_passes") {
    val catalogItemId =
        reference("catalog_item_id", CatalogItemTable, onDelete = ReferenceOption.CASCADE)
            .uniqueIndex()

    val name = varchar("name", 255)
    val description = varchar("description", 500).nullable()

    // Tour pass-specific assets/metadata
    val coverId = varchar("cover_id", 10).nullable()
}