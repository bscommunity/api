package org.bscm.interactions

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.serialization.json.*
import org.bscm.interactions.commands.PublishCommand
import org.bscm.services.track.resolvers.jsonClient

object CommandHandler {
    suspend fun handle(call: ApplicationCall, payload: JsonObject) {
        val data = payload["data"]?.jsonObject ?: run {
            call.respond(HttpStatusCode.BadRequest, I18n.t(payload["locale"]?.jsonPrimitive?.contentOrNull, "missing_data"))
            return
        }
        val name = data["name"]?.jsonPrimitive?.contentOrNull
        val locale = payload["locale"]?.jsonPrimitive?.contentOrNull ?: payload["guild_locale"]?.jsonPrimitive?.contentOrNull

        when (name) {
            "publish" -> {
                PublishCommand.handle(call, payload, data, locale)
                return
            }
            else -> {
                call.respond(HttpStatusCode.NotImplemented, I18n.t(locale, "unsupported_command"))
                return
            }
        }
    }

    fun WebhookPayload.toInteractionJson(ephemeral: Boolean = false): JsonObject = buildJsonObject {
        put("type", 4) // CHANNEL_MESSAGE_WITH_SOURCE
        put("data", buildJsonObject {
            if (ephemeral) put("flags", 64)
            content?.let { put("content", it) }
            if (embeds.isNotEmpty()) {
                put("embeds", buildJsonArray {
                    embeds.forEach { add(jsonClient.encodeToJsonElement(Embed.serializer(), it)) }
                })
            }
            if (components.isNotEmpty()) {
                put("components", buildJsonArray {
                    components.forEach { add(jsonClient.encodeToJsonElement(ActionRow.serializer(), it)) }
                })
            }
        })
    }

    fun ephemeralMessage(block: MessageBuilder.() -> Unit): JsonObject = message(block).toInteractionJson(ephemeral = true)

    fun immediateEphemeralResponse(block: MessageBuilder.() -> Unit): JsonObject =
        message(block).toInteractionJson(ephemeral = true)
}