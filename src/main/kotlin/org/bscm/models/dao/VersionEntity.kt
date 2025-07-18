package org.bscm.models.dao

import org.bscm.models.tables.VersionTable
import org.jetbrains.exposed.dao.ULongEntity
import org.jetbrains.exposed.dao.ULongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class VersionEntity(id: EntityID<ULong>) : ULongEntity(id) {
    companion object : ULongEntityClass<VersionEntity>(VersionTable)

    // Reference to the parent Chart
    var chart by ChartEntity referencedOn VersionTable.chartId
    var chartId by VersionTable.chartId // Necessary to prevent loading the entire ChartEntity when batching queries

    var index by VersionTable.index
    var duration by VersionTable.duration
    var notesAmount by VersionTable.notesAmount
    var effectsAmount by VersionTable.effectsAmount
    var bpm by VersionTable.bpm
    var isDeluxe by VersionTable.isDeluxe
    var isExplicit by VersionTable.isExplicit
    var difficulty by VersionTable.difficulty
    var bundleUrl by VersionTable.bundleUrl
    var previewUrl by VersionTable.previewUrl
    var downloadsAmount by VersionTable.downloadsAmount
    var knownIssues by VersionTable.knownIssues // Complex property (JSON)
    var publishedAt by VersionTable.publishedAt
}
