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

    val versionableInfo: VersionableInfoEntity?
        get() = VersionableInfoEntity.findById(id)

    val latestVersion: VersionEntity?
        get() = versionableInfo?.latestVersion

    val versions
        get() = CatalogItemEntity[id].versions

    val versionsCount: Int
        get() = versionableInfo?.versionsCount ?: 0

    var bundleHash: String?
        get() = versionableInfo?.bundleHash
        set(value) {
            versionableInfo?.bundleHash = value
        }

    var difficulty by ChartTable.difficulty

    var notesAmount by ChartTable.notesAmount
    var effectsAmount by ChartTable.effectsAmount

    var isDeluxe by ChartTable.isDeluxe
    var isExplicit by ChartTable.isExplicit
}
