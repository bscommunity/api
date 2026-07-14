package org.bscm.services

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.logging.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.bscm.repository.BundleUrlCacheRepository
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.concurrent.ConcurrentHashMap

private val log = KtorSimpleLogger("BundleDownloadService")
private val discordJson = Json { ignoreUnknownKeys = true }

class BundleDownloadService(
    private val cacheRepository: BundleUrlCacheRepository,
    private val client: HttpClient,
    private val botToken: String,
    private val channelId: String,
) {
    companion object {
        private val locks = ConcurrentHashMap<String, Mutex>()
    }

    suspend fun resolveBundleUrl(catalogItemId: String, messageId: String): String {
        val cached = suspendTransaction { cacheRepository.getCachedUrl(catalogItemId) }
        if (cached != null) return cached

        val mutex = locks.computeIfAbsent(messageId) { Mutex() }
        return mutex.withLock {
            val cachedAgain = suspendTransaction { cacheRepository.getCachedUrl(catalogItemId) }
            if (cachedAgain != null) return@withLock cachedAgain

            val url = fetchAttachmentUrlFromDiscord(messageId)
            suspendTransaction { cacheRepository.cacheUrl(catalogItemId, url) }
            url
        }
    }

    private suspend fun fetchAttachmentUrlFromDiscord(messageId: String): String {
        val response: HttpResponse = client.get(
            "https://discord.com/api/v10/channels/$channelId/messages/$messageId"
        ) {
            header(HttpHeaders.Authorization, "Bot $botToken")
        }

        if (!response.status.isSuccess()) {
            val msg = "Discord API returned ${response.status} for message $messageId"
            log.error(msg)
            throw RuntimeException(msg)
        }

        val body = response.bodyAsText()
        val message = discordJson.parseToJsonElement(body).jsonObject

        val attachments = message["attachments"]?.jsonArray
            ?: throw RuntimeException("No attachments found in Discord message $messageId")

        val lastAttachment = attachments.last().jsonObject
        return lastAttachment["url"]?.jsonPrimitive?.content
            ?: throw RuntimeException("No URL found in attachment for message $messageId")
    }
}
