package org.bscm.models.dao

import org.bscm.models.tables.DownloadEventTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.java.UUIDEntity
import org.jetbrains.exposed.v1.dao.java.UUIDEntityClass
import java.util.*

class DownloadEventEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<DownloadEventEntity>(DownloadEventTable)

    var catalogItem by CatalogItemEntity referencedOn DownloadEventTable.catalogItemId
    var eventType by DownloadEventTable.eventType
    var createdAt by DownloadEventTable.createdAt
}
