package org.bscm.repository

import org.bscm.models.Version
import org.bscm.models.dao.CatalogItemEntity
import org.bscm.models.dao.VersionEntity
import org.bscm.models.dto.version.CreateVersionRequest
import org.bscm.models.enums.CatalogItemType
import org.bscm.models.interfaces.IVersionRepository
import org.bscm.models.mappers.VersionMapper.entityToVersion
import org.bscm.models.tables.VersionTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

class VersionRepository : IVersionRepository {
    override suspend fun getVersionById(id: ULong): Version? = suspendTransaction {
        VersionEntity.findById(id)?.let { entityToVersion(it) }
    }

    override suspend fun getVersions(catalogItemId: String): List<Version> = suspendTransaction {
        VersionEntity.find { VersionTable.catalogItemId eq catalogItemId }
            .orderBy(VersionTable.versionCode to SortOrder.ASC)
            .map { entityToVersion(it) }
    }

    // TODO: Optimize with ROW_NUMBER() OVER (PARTITION BY catalog_item_id ORDER BY version_code DESC)
    // or a MAX(version_code) GROUP BY catalog_item_id self-join to push aggregation to SQL.
    override suspend fun getLatestVersionsByCatalogItemIds(catalogItemIds: List<String>): List<Version> =
        suspendTransaction {
            if (catalogItemIds.isEmpty()) return@suspendTransaction emptyList()

            val allVersions = VersionTable.selectAll()
                .where { VersionTable.catalogItemId inList catalogItemIds }
                .toList()

            allVersions
                .groupBy { it[VersionTable.catalogItemId].value }
                .mapValues { (_, versions) ->
                    versions.maxBy { it[VersionTable.versionCode] }
                }
                .values
                .map { row ->
                    entityToVersion(VersionEntity.wrapRow(row))
                }
        }

    override suspend fun addVersion(catalogItemId: String, version: CreateVersionRequest, bundleHash: String): Version = suspendTransaction {
        val itemType = CatalogItemEntity[catalogItemId].type
        require(itemType in listOf(CatalogItemType.CHART, CatalogItemType.THEME)) {
            "Catalog item $catalogItemId of type $itemType is not versionable"
        }

        val latestCode = VersionEntity.find { VersionTable.catalogItemId eq catalogItemId }
            .maxOfOrNull { it.versionCode } ?: 0

        val newVersion = if (version.id != null) {
            VersionEntity.new(version.id) {
                this.catalogItem = CatalogItemEntity[catalogItemId]
                this.versionCode = latestCode + 1
                this.fileSizeBytes = version.fileSizeBytes
                this.changelog = version.changelog.ifBlank { null }
                this.bundleHash = bundleHash
            }
        } else {
            VersionEntity.new {
                this.catalogItem = CatalogItemEntity[catalogItemId]
                this.versionCode = latestCode + 1
                this.fileSizeBytes = version.fileSizeBytes
                this.changelog = version.changelog.ifBlank { null }
                this.bundleHash = bundleHash
            }
        }

        entityToVersion(newVersion)
    }

    override suspend fun removeVersion(versionId: ULong): Boolean =
        suspendTransaction {
            val versionEntity = VersionEntity.findById(versionId)
                ?: throw IllegalArgumentException("Version $versionId not found")

            val catalogItemId = versionEntity.catalogItem.id.value

            val latestVersion = VersionEntity.find { VersionTable.catalogItemId eq catalogItemId }
                .orderBy(VersionTable.versionCode to SortOrder.DESC)
                .limit(1)
                .singleOrNull() ?: throw IllegalStateException("No versions found")

            if (latestVersion.id.value != versionId) {
                throw IllegalArgumentException("Only the latest version can be removed")
            }

            val versionCount = VersionEntity.find { VersionTable.catalogItemId eq catalogItemId }.count()
            if (versionCount == 1L) {
                throw IllegalArgumentException("Cannot remove the only version of this item")
            }

            versionEntity.delete()
            true
        }
}
