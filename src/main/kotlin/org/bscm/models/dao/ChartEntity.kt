package org.bscm.models.dao

import org.bscm.models.tables.ChartTable
import org.bscm.models.tables.VersionTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.Entity
import org.jetbrains.exposed.v1.dao.EntityClass

class ChartEntity(
    id: EntityID<String>
) : Entity<String>(id) {

    companion object :
        EntityClass<String, ChartEntity>(ChartTable)

    var track by TrackEntity referencedOn
            ChartTable.trackId

    val latestVersion: VersionEntity?
        get() = VersionEntity.find { VersionTable.catalogItemId eq id }
            .orderBy(VersionTable.versionCode to SortOrder.DESC)
            .limit(1)
            .singleOrNull()

    val versions
        get() = VersionEntity.find { VersionTable.catalogItemId eq id }

    val versionsCount: Int
        get() = VersionEntity.find { VersionTable.catalogItemId eq id }.count().toInt()

    val bundleHash: String?
        get() = latestVersion?.bundleHash

    var difficulty by ChartTable.difficulty

    var notesAmount by ChartTable.notesAmount
    var effectsAmount by ChartTable.effectsAmount

    var isDeluxe by ChartTable.isDeluxe
    var isExplicit by ChartTable.isExplicit
}
