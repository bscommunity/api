package org.bscm.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*
import org.bscm.interactions.CommandHandler
import org.bscm.interactions.I18n
import org.bscm.utils.Ed25519Utils.verifyEd25519

fun Route.interactionsRoutes(publicKey: String?) {
    post("/interactions") {
        val signature = call.request.header("X-Signature-Ed25519")
        val timestamp = call.request.header("X-Signature-Timestamp")
        val bodyText = call.receiveText()

        if (publicKey.isNullOrBlank() || signature.isNullOrBlank() || timestamp.isNullOrBlank()) {
            call.respond(HttpStatusCode.Unauthorized, "invalid request signature")
            return@post
        }

        if (!verifyEd25519(publicKey, signature, (timestamp + bodyText).toByteArray(Charsets.UTF_8))) {
            call.respond(HttpStatusCode.Unauthorized, "invalid request signature")
            return@post
        }

        val json = try {
            Json.parseToJsonElement(bodyText).jsonObject
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, "invalid json body")
            return@post
        }

        // println("Received interaction: $json")

        when (json["type"]?.jsonPrimitive?.intOrNull) {
            1 -> call.respond(HttpStatusCode.OK, buildJsonObject { put("type", 1) }) // Discord's default PING (obligatory)
            2 -> CommandHandler.handle(call, json) // APPLICATION_COMMAND
            else -> call.respond(HttpStatusCode.NotImplemented, I18n.t(json["locale"]?.jsonPrimitive?.contentOrNull, "unsupported_interaction_type"))
        }
    }
}
