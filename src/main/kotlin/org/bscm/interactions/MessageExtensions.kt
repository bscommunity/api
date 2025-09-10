package org.bscm.interactions

import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.bscm.plugins.jsonClient
import org.bscm.services.ActionRow
import org.bscm.services.WebhookEmbed
import org.bscm.services.WebhookPayload
import org.bscm.services.message

suspend fun ApplicationCall.respondJson(json: JsonObject) = respond(json)

fun WebhookPayload.toInteractionJson(ephemeral: Boolean = false): JsonObject = buildJsonObject {
    put("type", 4) // CHANNEL_MESSAGE_WITH_SOURCE
    put("data", buildJsonObject {
        if (ephemeral) put("flags", 64)
        content?.let { put("content", it) }
        if (embeds.isNotEmpty()) {
            put("embeds", buildJsonArray {
                embeds.forEach { add(jsonClient.encodeToJsonElement(WebhookEmbed.serializer(), it)) }
            })
        }
        if (components.isNotEmpty()) {
            put("components", buildJsonArray {
                components.forEach { add(jsonClient.encodeToJsonElement(ActionRow.serializer(), it)) }
            })
        }
    })
}

fun ephemeralMessage(content: String): JsonObject = message { content(content) }.toInteractionJson(ephemeral = true)

