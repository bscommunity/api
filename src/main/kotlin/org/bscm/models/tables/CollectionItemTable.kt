
package org.bscm.models.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.javatime.datetime

object CollectionItemTable : IntIdTable("collection_items") {
    val collectionId = reference("collection_id", CollectionTable, onDelete = ReferenceOption.CASCADE)
    val contentId = reference("content_id", CatalogItemTable, onDelete = ReferenceOption.CASCADE)
    val addedAt = datetime("added_at")

    init {
        index(false, collectionId, addedAt, contentId)
        uniqueIndex(collectionId, contentId) // avoid duplicates
    }
}