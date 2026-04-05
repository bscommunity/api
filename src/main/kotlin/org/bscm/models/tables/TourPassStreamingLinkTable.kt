package org.bscm.models.tables

import org.jetbrains.exposed.dao.id.CompositeIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object TourPassStreamingLinkTable : CompositeIdTable("tour_pass_streaming_links") {
    val tourPassId = reference("tour_pass_id", TourPassTable, onDelete = ReferenceOption.CASCADE)
    val streamingLinkId = reference("streaming_link_id", StreamingLinkTable, onDelete = ReferenceOption.CASCADE)

    init {
        addIdColumn(tourPassId)
        addIdColumn(streamingLinkId)
    }

    override val primaryKey = PrimaryKey(
        tourPassId,
        streamingLinkId,
    )
}

