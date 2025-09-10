package org.bscm.interactions

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.bscm.interactions.commands.PublishCommand

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
                PublishCommand.handle(call, data, locale)
                return
            }
            else -> {
                call.respond(HttpStatusCode.NotImplemented, I18n.t(locale, "unsupported_command"))
                return
            }
        }
    }
}
