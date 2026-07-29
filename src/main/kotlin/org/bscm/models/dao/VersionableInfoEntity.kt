package org.bscm.models.dao

import org.bscm.models.tables.VersionableInfoTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.Entity
import org.jetbrains.exposed.v1.dao.EntityClass

class VersionableInfoEntity(
    id: EntityID<String>
) : Entity<String>(id) {

    companion object :
        EntityClass<String, VersionableInfoEntity>(VersionableInfoTable)

    var versionsCount by VersionableInfoTable.versionsCount

    var latestVersion by VersionEntity optionalReferencedOn
            VersionableInfoTable.latestVersionId

    var bundleHash by VersionableInfoTable.bundleHash
}
