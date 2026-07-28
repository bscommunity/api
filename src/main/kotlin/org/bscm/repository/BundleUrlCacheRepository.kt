package org.bscm.repository

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.bscm.models.tables.BundleUrlCacheTable
import org.bscm.models.tables.CatalogItemTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.time.Instant

class BundleUrlCacheRepository {
    suspend fun getCachedUrl(catalogItemId: String): String? = suspendTransaction {
        val row = BundleUrlCacheTable.selectAll()
            .where { BundleUrlCacheTable.catalogItemId eq EntityID(catalogItemId, CatalogItemTable) }
            .singleOrNull()

        if (row != null && row[BundleUrlCacheTable.expiresAt] > Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())) {
            row[BundleUrlCacheTable.bundleUrl]
        } else null
    }

    suspend fun cacheUrl(catalogItemId: String, url: String, expiresAt: Instant) = suspendTransaction {
        val entityId = EntityID(catalogItemId, CatalogItemTable)
        val expiresAtLdt = expiresAt.toLocalDateTime(TimeZone.currentSystemDefault())
        val existing = BundleUrlCacheTable.selectAll()
            .where { BundleUrlCacheTable.catalogItemId eq entityId }
            .singleOrNull()

        if (existing != null) {
            BundleUrlCacheTable.update({ BundleUrlCacheTable.catalogItemId eq entityId }) {
                it[BundleUrlCacheTable.bundleUrl] = url
                it[BundleUrlCacheTable.expiresAt] = expiresAtLdt
                it[BundleUrlCacheTable.lastValidatedAt] = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            }
        } else {
            BundleUrlCacheTable.insert {
                it[BundleUrlCacheTable.catalogItemId] = entityId
                it[BundleUrlCacheTable.bundleUrl] = url
                it[BundleUrlCacheTable.expiresAt] = expiresAtLdt
                it[BundleUrlCacheTable.lastValidatedAt] = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            }
        }
    }
}
