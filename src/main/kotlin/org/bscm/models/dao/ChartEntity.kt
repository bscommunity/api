package org.bscm.models.dao

import org.bscm.models.tables.ChartTable
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID

class ChartEntity(
    id: EntityID<String>
) : Entity<String>(id) {

    companion object :
        EntityClass<String, ChartEntity>(ChartTable)

    var track by TrackEntity referencedOn
            ChartTable.trackId

    val latestVersion: VersionEntity?
        get() = CatalogItemEntity[id].latestVersion

    val versions
        get() = CatalogItemEntity[id].versions

    val versionsCount: Int
        get() = CatalogItemEntity[id].versionsCount

    var difficulty by ChartTable.difficulty

    var notesAmount by ChartTable.notesAmount
    var effectsAmount by ChartTable.effectsAmount

    var isDeluxe by ChartTable.isDeluxe
    var isExplicit by ChartTable.isExplicit
}
