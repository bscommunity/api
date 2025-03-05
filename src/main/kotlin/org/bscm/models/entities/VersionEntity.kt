package org.bscm.models.entities

import org.bscm.models.tables.VersionTable
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class VersionEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<VersionEntity>(VersionTable)

    // Reference to the parent Chart
    var chart by ChartEntity referencedOn VersionTable.chartId

    var duration by VersionTable.duration
    var notesAmount by VersionTable.notesAmount
    var effectsAmount by VersionTable.effectsAmount
    var bpm by VersionTable.bpm
    var chartUrl by VersionTable.chartUrl
    var downloadsAmount by VersionTable.downloadsAmount
    var knownIssues by VersionTable.knownIssues // Complex property (JSON)
    var publishedAt by VersionTable.publishedAt
}