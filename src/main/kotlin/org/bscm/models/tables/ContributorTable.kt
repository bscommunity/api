package org.bscm.models.tables

import org.bscm.models.enums.ContributorRole
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.CurrentDateTime
import org.jetbrains.exposed.v1.javatime.datetime

object ContributorTable : LongIdTable("contributors") {
    val catalogItemId = reference("catalog_item_id", CatalogItemTable, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val role = enumerationByName("role", 30, ContributorRole::class)

    val note = varchar("note", 280).nullable()
    val joinedAt = datetime("joined_at").defaultExpression(CurrentDateTime)

    init {
        uniqueIndex(catalogItemId, userId, role)
    }
}
