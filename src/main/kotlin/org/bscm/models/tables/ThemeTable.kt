package org.bscm.models.tables

import org.jetbrains.exposed.dao.id.ULongIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object ThemeTable : ULongIdTable("themes") {
    val catalogId =
        reference("catalog_item_id", CatalogItemTable, onDelete = ReferenceOption.CASCADE)
            .uniqueIndex()

    val versionsCount = integer("versions_count").default(0)

    val latestVersionId =
        reference("latest_version_id", VersionTable, onDelete = ReferenceOption.SET_NULL)
            .nullable()

    val name = varchar("name", 255)
    val description = varchar("description", 500).nullable()

    // Theme-specific assets/metadata
    val coverId = varchar("cover_id", 10).nullable()
    val displayArtId = varchar("display_art_id", 20).nullable()
    val replaces = varchar("replaces", 255)

    init {
        index(false, name)
    }
}