package org.bscm.repository

import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class BundleUrlCacheRepository {
    suspend fun getCachedUrl(catalogItemId: String): String? = newSuspendedTransaction {
        // TODO Bloco 6: query from BundleUrlCacheTable
        null
    }

    suspend fun cacheUrl(catalogItemId: String, url: String) = newSuspendedTransaction {
        // TODO Bloco 6: insert into BundleUrlCacheTable
    }
}
