package org.bscm.models.dao

import org.bscm.models.tables.VersionTable
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.*

class VersionEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<VersionEntity>(VersionTable)

    // Reference to the parent Chart
    var chart by ChartEntity referencedOn VersionTable.chartId

    var index by VersionTable.index
    var duration by VersionTable.duration
    var notesAmount by VersionTable.notesAmount
    var effectsAmount by VersionTable.effectsAmount
    var bpm by VersionTable.bpm
    var chartUrl by VersionTable.chartUrl
    var chartPreviewUrls by VersionTable.chartPreviewUrls
    var downloadsAmount by VersionTable.downloadsAmount
    var knownIssues by VersionTable.knownIssues // Complex property (JSON)
    var publishedAt by VersionTable.publishedAt
}
