package org.bscm.repository

import org.bscm.models.tables.BundleUrlCacheTable
import org.bscm.models.tables.CatalogItemTable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime

class BundleUrlCacheRepository {
    suspend fun getCachedUrl(catalogItemId: String): String? = newSuspendedTransaction {
        val row = BundleUrlCacheTable.selectAll()
            .where { BundleUrlCacheTable.catalogItemId eq EntityID(catalogItemId, CatalogItemTable) }
            .singleOrNull()

        if (row != null && row[BundleUrlCacheTable.expiresAt].isAfter(LocalDateTime.now())) {
            row[BundleUrlCacheTable.bundleUrl]
        } else null
    }

    suspend fun cacheUrl(catalogItemId: String, url: String) = newSuspendedTransaction {
        val entityId = EntityID(catalogItemId, CatalogItemTable)
        val existing = BundleUrlCacheTable.selectAll()
            .where { BundleUrlCacheTable.catalogItemId eq entityId }
            .singleOrNull()

        if (existing != null) {
            BundleUrlCacheTable.update({ BundleUrlCacheTable.catalogItemId eq entityId }) {
                it[BundleUrlCacheTable.bundleUrl] = url
                it[BundleUrlCacheTable.expiresAt] = LocalDateTime.now().plusHours(1)
                it[BundleUrlCacheTable.lastValidatedAt] = LocalDateTime.now()
            }
        } else {
            BundleUrlCacheTable.insert {
                it[BundleUrlCacheTable.catalogItemId] = entityId
                it[BundleUrlCacheTable.bundleUrl] = url
                it[BundleUrlCacheTable.expiresAt] = LocalDateTime.now().plusHours(1)
                it[BundleUrlCacheTable.lastValidatedAt] = LocalDateTime.now()
            }
        }
    }
}
