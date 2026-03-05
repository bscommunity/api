
package org.bscm.models.tables

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.datetime

object CollectionItemTable : IntIdTable("collection_items") {
    val collectionId = reference("collection_id", CollectionTable, onDelete = ReferenceOption.CASCADE)
    val contentId = reference("content_id", ContentTable, onDelete = ReferenceOption.CASCADE)
    val addedAt = datetime("added_at")

    init {
        index(false, collectionId, addedAt, contentId)
        uniqueIndex(collectionId, contentId) // avoid duplicates
    }
}