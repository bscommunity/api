package org.bscm.models.tables

import org.bscm.models.enums.CatalogItemStatus
import org.bscm.models.enums.CatalogItemType
import org.bscm.utils.NanoIdUtils
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

object CatalogItemTable : IdTable<String>("catalog_items") {
    override val id = varchar("id", 10)
        .clientDefault { NanoIdUtils.generateOptimized(10, "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ", 63, 16) }
        .entityId()
        .uniqueIndex()
    val type = enumerationByName("type", 20, CatalogItemType::class)
    val status = enumerationByName("status", 20, CatalogItemStatus::class)

    val previewUrl = varchar("preview_url", 100).nullable()

    val isPublic = bool("is_public").default(true)
    val isFeatured = bool("is_featured").default(false)

    // Aggregated/derived fields useful for queries
    val downloadsSum = integer("downloads_sum").default(0)

    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val publishedAt = datetime("published_at").nullable()
    val updatedAt = datetime("updated_at").nullable()

    val authorId = reference("author_id", UserTable, ReferenceOption.CASCADE)

    init {
        index(false, type)
    }
}