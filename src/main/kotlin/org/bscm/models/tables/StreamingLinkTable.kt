package org.bscm.models.tables

import org.bscm.models.enums.StreamingPlatform
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption

object StreamingLinkTable : UUIDTable("streaming_link") {
    val chartId = reference("chart_id", ChartTable, onDelete = ReferenceOption.CASCADE)
    val platform = enumerationByName("platform", 20, StreamingPlatform::class)
    val url = varchar("url", 512)

    init {
        index(true, chartId, platform)
    }
}