package org.bscm.models.dao

import org.bscm.models.tables.ChartTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.Entity
import org.jetbrains.exposed.v1.dao.EntityClass

class ChartEntity(
    id: EntityID<String>
) : Entity<String>(id) {

    companion object :
        EntityClass<String, ChartEntity>(ChartTable)

    var track by TrackEntity referencedOn
            ChartTable.trackId

    val versionableItem: VersionableItemEntity?
        get() = VersionableItemEntity.findById(id)

    val latestVersion: VersionEntity?
        get() = versionableItem?.latestVersion

    val versions
        get() = CatalogItemEntity[id].versions

    val versionsCount: Int
        get() = versionableItem?.versionsCount ?: 0

    var bundleHash: String?
        get() = versionableItem?.bundleHash
        set(value) {
            versionableItem?.bundleHash = value
        }

    var difficulty by ChartTable.difficulty

    var notesAmount by ChartTable.notesAmount
    var effectsAmount by ChartTable.effectsAmount

    var isDeluxe by ChartTable.isDeluxe
    var isExplicit by ChartTable.isExplicit
}
