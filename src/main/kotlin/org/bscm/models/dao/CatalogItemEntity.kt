package org.bscm.models.dao

import org.bscm.models.tables.*
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityClass
import org.jetbrains.exposed.dao.id.EntityID

class CatalogItemEntity(
    id: EntityID<String>
) : Entity<String>(id) {

    companion object :
        EntityClass<String, CatalogItemEntity>(CatalogItemTable)

    var type by CatalogItemTable.type
    var status by CatalogItemTable.status

    var previewVideoId by CatalogItemTable.previewVideoId

    var isPublic by CatalogItemTable.isPublic
    var isFeatured by CatalogItemTable.isFeatured

    var downloadsSum by CatalogItemTable.downloadsSum

    var versionsCount by CatalogItemTable.versionsCount

    var latestVersion by VersionEntity optionalReferencedOn
            CatalogItemTable.latestVersionId

    val versions by VersionEntity referrersOn
            VersionTable.catalogItemId

    var createdAt by CatalogItemTable.createdAt
    var publishedAt by CatalogItemTable.publishedAt
    var updatedAt by CatalogItemTable.updatedAt

    var author by UserEntity referencedOn CatalogItemTable.authorId

    val contributors by ContributorEntity via ContributorTable

    val chart by ChartEntity optionalBackReferencedOn ChartTable.catalogItemId
    val theme by ThemeEntity optionalBackReferencedOn ThemeTable.catalogItemId
    val tourPass by TourPassEntity optionalBackReferencedOn TourPassTable.catalogItemId
}