package org.bscm.models.tables

import org.jetbrains.exposed.dao.id.ULongIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

abstract class CatalogItemTable(name: String) : ULongIdTable(name) {
    val contentId = reference("content_id", ContentTable, onDelete = ReferenceOption.CASCADE).uniqueIndex()

    val coverUrl = varchar("cover_url", 255)
    val isPublic = bool("is_public").default(true)
    val isFeatured = bool("is_featured").default(false)

    // Aggregated/derived fields useful for queries
    val downloadsSum = integer("downloads_sum").default(0)
    val latestUpdatedAt = datetime("latest_updated_at").nullable()

    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").nullable()

    val authorId = reference("author_id", UserTable, ReferenceOption.CASCADE)
}