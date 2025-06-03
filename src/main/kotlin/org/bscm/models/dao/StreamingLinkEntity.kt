package org.bscm.models.dao

import org.bscm.models.tables.StreamingLinkTable
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.*

class StreamingLinkEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<StreamingLinkEntity>(StreamingLinkTable)

    var chart by ChartEntity referencedOn StreamingLinkTable.chartId
    var chartId by StreamingLinkTable.chartId
    var platform by StreamingLinkTable.platform
    var url by StreamingLinkTable.url
}
