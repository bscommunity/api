package org.bscm.services

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.logging.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.bscm.repository.BundleUrlCacheRepository
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

private val log = KtorSimpleLogger("BundleDownloadService")
private val discordJson = Json { ignoreUnknownKeys = true }

class BundleDownloadService(
    private val cacheRepository: BundleUrlCacheRepository,
    private val client: HttpClient,
    private val botToken: String,
    private val channelId: String,
) {
    suspend fun resolveBundleUrl(catalogItemId: String, messageId: String): String = newSuspendedTransaction {
        val cached = cacheRepository.getCachedUrl(catalogItemId)
        if (cached != null) return@newSuspendedTransaction cached

        val url = fetchAttachmentUrlFromDiscord(messageId)
        cacheRepository.cacheUrl(catalogItemId, url)
        url
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
