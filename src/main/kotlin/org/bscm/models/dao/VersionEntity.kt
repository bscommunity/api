package org.bscm.models.dao

import org.bscm.models.tables.VersionTable
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.ULongEntity
import org.jetbrains.exposed.dao.id.EntityID

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

    var createdAt by VersionTable.createdAt
}
