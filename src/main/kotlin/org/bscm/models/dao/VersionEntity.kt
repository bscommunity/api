package org.bscm.models.dao

import org.bscm.models.tables.VersionTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.EntityClass
import org.jetbrains.exposed.v1.dao.ULongEntity

class VersionEntity(
    id: EntityID<ULong>
) : ULongEntity(id) {

    companion object :
        EntityClass<ULong, VersionEntity>(VersionTable)

    var catalogItem by CatalogItemEntity referencedOn VersionTable.catalogItemId

    var versionCode by VersionTable.versionCode

    var downloadsAmount by VersionTable.downloadsAmount

    var fileSizeBytes by VersionTable.fileSizeBytes

    var changelog by VersionTable.changelog

    var discordAttachmentId by VersionTable.discordAttachmentId

    var bundleHash by VersionTable.bundleHash

    var createdAt by VersionTable.createdAt
}
