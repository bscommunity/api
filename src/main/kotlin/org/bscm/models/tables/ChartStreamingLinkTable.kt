package org.bscm.models.tables

import org.jetbrains.exposed.dao.id.CompositeIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object ChartStreamingLinkTable : CompositeIdTable("chart_streaming_link") {
    val chartId = reference("chart_id", ChartTable, onDelete = ReferenceOption.CASCADE)
    val streamingLinkId = reference("streaming_link_id", StreamingLinkTable, onDelete = ReferenceOption.CASCADE)

    init {
        addIdColumn(chartId)
        addIdColumn(streamingLinkId)

        // index(false, chartId, streamingLinkId)
    }

    override val primaryKey = PrimaryKey(
        chartId,
        streamingLinkId
    )
}