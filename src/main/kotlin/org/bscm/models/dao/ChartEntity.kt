package org.bscm.models.dao

import org.bscm.models.tables.ChartTable
import org.jetbrains.exposed.dao.ULongEntity
import org.jetbrains.exposed.dao.ULongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class ChartEntity(
    id: EntityID<ULong>
) : ULongEntity(id) {

    companion object :
        ULongEntityClass<ChartEntity>(ChartTable)

    var catalogItem by CatalogItemEntity referencedOn
            ChartTable.catalogItemId

    var track by TrackEntity referencedOn
            ChartTable.trackId

    val latestVersion: VersionEntity?
        get() = catalogItem.latestVersion

    val versions
        get() = catalogItem.versions

    val versionsCount: Int
        get() = catalogItem.versionsCount

    var difficulty by ChartTable.difficulty

    var notesAmount by ChartTable.notesAmount
    var effectsAmount by ChartTable.effectsAmount

    var isDeluxe by ChartTable.isDeluxe
    var isExplicit by ChartTable.isExplicit
}