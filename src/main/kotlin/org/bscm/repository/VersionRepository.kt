package org.bscm.repository

import org.bscm.models.Version
import org.bscm.models.dao.CatalogItemEntity
import org.bscm.models.dao.VersionEntity
import org.bscm.models.dto.version.CreateVersionRequest
import org.bscm.models.interfaces.IVersionRepository
import org.bscm.models.mappers.VersionMapper.entityToVersion
import org.bscm.models.tables.CatalogItemTable
import org.bscm.models.tables.VersionTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.select
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

    override suspend fun getLatestVersionsByCatalogItemIds(catalogItemIds: List<String>): List<Version> =
        suspendTransaction {
            if (catalogItemIds.isEmpty()) return@suspendTransaction emptyList()

            val rows = (CatalogItemTable innerJoin VersionTable)
                .select(VersionTable.columns)
                .where {
                    (CatalogItemTable.id inList catalogItemIds) and
                            (CatalogItemTable.latestVersionId eq VersionTable.id)
                }
                .toList()

            rows.mapNotNull { row ->
                row.getOrNull(VersionTable.id)?.let { _ ->
                    entityToVersion(VersionEntity.wrapRow(row))
                }
            }
        }

    override suspend fun addVersion(catalogItemId: String, version: CreateVersionRequest): Version = suspendTransaction {
        val latestCode = VersionEntity.find { VersionTable.catalogItemId eq catalogItemId }
            .maxOfOrNull { it.versionCode } ?: 0

        val newVersion = if (version.id != null) {
            VersionEntity.new(version.id) {
                this.catalogItem = CatalogItemEntity[catalogItemId]
                this.versionCode = latestCode + 1
                this.fileSizeBytes = version.fileSizeBytes
                this.changelog = version.changelog.joinToString("\n").ifBlank { null }
            }
        } else {
            VersionEntity.new {
                this.catalogItem = CatalogItemEntity[catalogItemId]
                this.versionCode = latestCode + 1
                this.fileSizeBytes = version.fileSizeBytes
                this.changelog = version.changelog.joinToString("\n").ifBlank { null }
            }
        }

        CatalogItemEntity.findByIdAndUpdate(catalogItemId) {
            it.latestVersion = newVersion
            it.versionsCount += 1
        }

        entityToVersion(newVersion)
    }

    override suspend fun removeVersion(versionId: ULong, currentLatestVersionId: String?, versionCount: Int): Boolean =
        suspendTransaction {
            if (currentLatestVersionId != versionId.toString()) {
                throw IllegalArgumentException(
                    "Only the latest version can be removed. " +
                            "Attempted $versionId but latest is $currentLatestVersionId"
                )
            }

            if (versionCount == 1) {
                throw IllegalArgumentException("Cannot remove the only version of chart")
            }

            val versionEntity = VersionEntity.findById(versionId)
                ?: throw IllegalArgumentException("Version $versionId not found")

            val catalogItemId = versionEntity.catalogItem.id.value

            versionEntity.delete()

            val nextLatest = VersionEntity.find { VersionTable.catalogItemId eq catalogItemId }
                .orderBy(VersionTable.versionCode to SortOrder.DESC)
                .limit(1)
                .singleOrNull() ?: throw IllegalStateException("No remaining versions found")

            CatalogItemEntity.findByIdAndUpdate(catalogItemId) {
                it.latestVersion = nextLatest
                it.versionsCount -= 1
            }

            true
        }
}
