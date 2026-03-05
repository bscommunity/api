package org.bscm.services

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.bscm.clients.applicationHttpClient
import org.bscm.clients.jsonClient
import org.bscm.interactions.ActionRow
import org.bscm.interactions.Embed
import org.bscm.interactions.WebhookPayload

/**
 * Service to handle Discord interaction responses including deferred responses and follow-ups.
 * Based on Discord's interaction response flow.
 */
class InteractionResponseService(
    private val botToken: String,
    private val applicationId: String,
) {
    companion object {
        // Discord Interaction Response Types
        const val PONG = 1
        const val CHANNEL_MESSAGE_WITH_SOURCE = 4
        const val DEFERRED_CHANNEL_MESSAGE_WITH_SOURCE = 5
        const val DEFERRED_UPDATE_MESSAGE = 6
        const val UPDATE_MESSAGE = 7

        // Message flags
        const val EPHEMERAL_FLAG = 64
    }

    /**
     * Creates a deferred response (shows "bot is thinking..." message).
     * This acknowledges the interaction and gives you 15 minutes to respond.
     */
    fun deferredResponse(ephemeral: Boolean = true): JsonObject = buildJsonObject {
        put("type", DEFERRED_CHANNEL_MESSAGE_WITH_SOURCE)
        if (ephemeral) {
            put("data", buildJsonObject {
                put("flags", EPHEMERAL_FLAG)
            })
        }
    }

    /**
     * Creates an initial response message.
     */
    fun initialResponse(payload: WebhookPayload, ephemeral: Boolean = false): JsonObject = buildJsonObject {
        put("type", CHANNEL_MESSAGE_WITH_SOURCE)
        put("data", buildJsonObject {
            if (ephemeral) put("flags", EPHEMERAL_FLAG)
            payload.content?.let { put("content", it) }
            if (payload.embeds.isNotEmpty()) {
                put("embeds", buildJsonArray {
                    payload.embeds.forEach { add(jsonClient.encodeToJsonElement(Embed.serializer(), it)) }
                })
            }
            if (payload.components.isNotEmpty()) {
                put("components", buildJsonArray {
                    payload.components.forEach { add(jsonClient.encodeToJsonElement(ActionRow.serializer(), it)) }
                })
            }
        })
    }

    /**
     * Edits the original interaction response.
     * Use this after a deferred response or to update the initial response.
     */
    suspend fun editOriginalResponse(
        interactionToken: String,
        payload: WebhookPayload
    ): Result<String> = runCatching {
        val url = "https://discord.com/api/v10/webhooks/$applicationId/$interactionToken/messages/@original"

        val json = buildJsonObject {
            payload.content?.let { put("content", it) }
            if (payload.embeds.isNotEmpty()) {
                put("embeds", buildJsonArray {
                    payload.embeds.forEach { add(jsonClient.encodeToJsonElement(Embed.serializer(), it)) }
                })
            }
            if (payload.components.isNotEmpty()) {
                put("components", buildJsonArray {
                    payload.components.forEach { add(jsonClient.encodeToJsonElement(ActionRow.serializer(), it)) }
                })
            }
        }

        val response: HttpResponse = applicationHttpClient.patch(url) {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(json.toString())
        }

        if (!response.status.isSuccess()) {
            throw Exception("Failed to edit response: ${response.status} - ${response.bodyAsText()}")
        }

        response.bodyAsText()
    }

    /**
     * Sends a follow-up message to an interaction.
     * Can be used to send additional messages after the initial response.
     */
    suspend fun followUp(
        interactionToken: String,
        payload: WebhookPayload,
        ephemeral: Boolean = false
    ): Result<String> = runCatching {
        val url = "https://discord.com/api/v10/webhooks/$applicationId/$interactionToken"

        val json = buildJsonObject {
            if (ephemeral) put("flags", EPHEMERAL_FLAG)
            payload.content?.let { put("content", it) }
            if (payload.embeds.isNotEmpty()) {
                put("embeds", buildJsonArray {
                    payload.embeds.forEach { add(jsonClient.encodeToJsonElement(Embed.serializer(), it)) }
                })
            }
            if (payload.components.isNotEmpty()) {
                put("components", buildJsonArray {
                    payload.components.forEach { add(jsonClient.encodeToJsonElement(ActionRow.serializer(), it)) }
                })
            }
        }

        val response: HttpResponse = applicationHttpClient.post(url) {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            setBody(json.toString())
        }

        if (!response.status.isSuccess()) {
            throw Exception("Failed to send follow-up: ${response.status} - ${response.bodyAsText()}")
        }

        response.bodyAsText()
    }

    /**
     * Deletes the original interaction response.
     */
    suspend fun deleteOriginalResponse(interactionToken: String): Result<Unit> = runCatching {
        val url = "https://discord.com/api/v10/webhooks/$applicationId/$interactionToken/messages/@original"

        val response: HttpResponse = applicationHttpClient.delete(url)

        if (!response.status.isSuccess()) {
            throw Exception("Failed to delete response: ${response.status} - ${response.bodyAsText()}")
        }
    }
}

