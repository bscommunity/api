package org.bscm.services

import io.ktor.client.*
import io.ktor.util.logging.*
import org.bscm.repository.BundleUrlCacheRepository
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

private val log = KtorSimpleLogger("BundleDownloadService")

class BundleDownloadService(
    private val cacheRepository: BundleUrlCacheRepository,
    private val client: HttpClient,
    private val botToken: String,
    private val channelId: String,
) {
    suspend fun resolveBundleUrl(catalogItemId: String, messageId: String): String = newSuspendedTransaction {
        cacheRepository.getCachedUrl(catalogItemId) ?: run {
            // TODO Bloco 6: fetch from Discord, cache result
            cacheRepository.getCachedUrl(catalogItemId) ?: "https://cdn.discordapp.com/attachments/$channelId/$messageId/bundle.zip"
        }
    }
}
