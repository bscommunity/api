package org.bscm.repository

import org.bscm.models.tables.BundleUrlCacheTable
import org.bscm.models.tables.CatalogItemTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.time.Instant

class BundleUrlCacheRepository {
    suspend fun getCachedUrl(catalogItemId: String): String? = suspendTransaction {
        val row = BundleUrlCacheTable.selectAll()
            .where { BundleUrlCacheTable.catalogItemId eq EntityID(catalogItemId, CatalogItemTable) }
            .singleOrNull()

        if (row != null && row[BundleUrlCacheTable.expiresAt].isAfter(LocalDateTime.now())) {
            row[BundleUrlCacheTable.bundleUrl]
        } else null
    }

    suspend fun cacheUrl(catalogItemId: String, url: String, expiresAt: Instant) = suspendTransaction {
        val entityId = EntityID(catalogItemId, CatalogItemTable)
        val expiresAtLdt = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochSecond(expiresAt.epochSeconds),
            ZoneId.systemDefault()
        )
        val existing = BundleUrlCacheTable.selectAll()
            .where { BundleUrlCacheTable.catalogItemId eq entityId }
            .singleOrNull()

        if (existing != null) {
            BundleUrlCacheTable.update({ BundleUrlCacheTable.catalogItemId eq entityId }) {
                it[BundleUrlCacheTable.bundleUrl] = url
                it[BundleUrlCacheTable.expiresAt] = expiresAtLdt
                it[BundleUrlCacheTable.lastValidatedAt] = LocalDateTime.now()
            }
        } else {
            BundleUrlCacheTable.insert {
                it[BundleUrlCacheTable.catalogItemId] = entityId
                it[BundleUrlCacheTable.bundleUrl] = url
                it[BundleUrlCacheTable.expiresAt] = expiresAtLdt
                it[BundleUrlCacheTable.lastValidatedAt] = LocalDateTime.now()
            }
        }
    }
}
