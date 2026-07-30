package org.bscm.models.dao

import org.bscm.models.tables.VersionableItemTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.Entity
import org.jetbrains.exposed.v1.dao.EntityClass

class VersionableItemEntity(
    id: EntityID<String>
) : Entity<String>(id) {

    companion object :
        EntityClass<String, VersionableItemEntity>(VersionableItemTable)

    var versionsCount by VersionableItemTable.versionsCount

    var latestVersion by VersionEntity optionalReferencedOn
            VersionableItemTable.latestVersionId

    var bundleHash by VersionableItemTable.bundleHash
}
