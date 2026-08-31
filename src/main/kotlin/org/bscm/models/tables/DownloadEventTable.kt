package org.bscm.models.tables

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.bscm.models.enums.DownloadEventType
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.datetime.datetime
import kotlin.time.Clock

object DownloadEventTable : UUIDTable("download_events") {
    val catalogItemId = reference("catalog_item_id", CatalogItemTable, onDelete = ReferenceOption.CASCADE)
    val eventType = enumerationByName("event_type", 10, DownloadEventType::class)
    val createdAt = datetime("created_at")
        .clientDefault { Clock.System.now().toLocalDateTime(TimeZone.UTC) }

    init {
        index(false, catalogItemId, createdAt)
        index(false, createdAt)
    }
}
