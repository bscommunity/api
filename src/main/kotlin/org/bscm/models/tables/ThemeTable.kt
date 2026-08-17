package org.bscm.models.tables

import org.bscm.models.enums.BeatstarThemeId
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable

object ThemeTable : IdTable<String>("themes") {
    override val id: Column<EntityID<String>> = varchar("id", 10).entityId()

    val name = varchar("name", 255)
    val replaces = enumeration("replaces", BeatstarThemeId::class)
    val originalArtwork = varchar("original_artwork", 512).nullable()
    val previewUrl = varchar("preview_url", 512).nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, name)
    }
}
