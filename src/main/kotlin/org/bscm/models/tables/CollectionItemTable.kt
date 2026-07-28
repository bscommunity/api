
package org.bscm.models.tables

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.datetime.datetime
import kotlin.time.Clock

object CollectionItemTable : IntIdTable("collection_items") {
    val collectionId = reference("collection_id", CollectionTable, onDelete = ReferenceOption.CASCADE)
    val contentId = reference("content_id", CatalogItemTable, onDelete = ReferenceOption.CASCADE)
    val addedAt = datetime("added_at")
        .clientDefault { Clock.System.now().toLocalDateTime(TimeZone.UTC) }

    init {
        index(false, collectionId, addedAt, contentId)
        uniqueIndex(collectionId, contentId) // avoid duplicates
    }
}