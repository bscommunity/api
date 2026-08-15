package org.bscm.models.tables

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.bscm.models.enums.ContributorRole
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.datetime.datetime
import kotlin.time.Clock

object ContributorTable : LongIdTable("contributors") {
    val catalogItemId = reference("catalog_item_id", CatalogItemTable, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE).index()
    val role = enumerationByName("role", 30, ContributorRole::class)

    val note = varchar("note", 280).nullable()
    val joinedAt = datetime("joined_at").clientDefault { Clock.System.now().toLocalDateTime(TimeZone.UTC) }

    init {
        uniqueIndex(catalogItemId, userId, role)
    }
}
