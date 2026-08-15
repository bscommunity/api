package org.bscm.models.tables

import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable

object AlbumTable : UUIDTable("albums") {
    val name = varchar("name", 200)
    val normalizedName = varchar("normalized_name", 200).uniqueIndex()
}
