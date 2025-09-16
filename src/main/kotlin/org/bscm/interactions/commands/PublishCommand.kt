package org.bscm.interactions.commands

import io.ktor.server.application.*
import io.ktor.server.response.*
import kotlinx.serialization.json.*
import org.bscm.interactions.CommandHandler.ephemeralMessage
import org.bscm.interactions.I18n

object PublishCommand {
    suspend fun ApplicationCall.respondJson(json: JsonObject) = respond(json)

    suspend fun handle(call: ApplicationCall, data: JsonObject, locale: String?) {
        val optionsArray = data["options"]?.jsonArray
        if (optionsArray == null || optionsArray.isEmpty()) {
            call.respondJson(ephemeralMessage { content(I18n.t(locale, "provide_required_attachments")) })
            return
        }

        val options = optionsArray.mapNotNull { it.jsonObject }
        fun findOption(n: String) = options.firstOrNull { it["name"]?.jsonPrimitive?.content == n }?.jsonObject

        val bundleOpt = findOption("bundle_zip")
        val chartOpt = findOption("chart_file")
        val gameplayOpt = findOption("gameplay_url")
        val explicitOpt = findOption("is_explicit")

        if (bundleOpt == null || chartOpt == null) {
            call.respondJson(ephemeralMessage { content(I18n.t(locale, "missing_required_attachments")) })
            return
        }

        val resolved = data["resolved"]?.jsonObject
        val attachments = resolved?.get("attachments")?.jsonObject
        fun attachmentLabel(opt: JsonObject?): String {
            if (opt == null) return I18n.t(locale, "null_label")
            val id = opt["value"]?.jsonPrimitive?.contentOrNull ?: return I18n.t(locale, "missing_id_label")
            val att = attachments?.get(id)?.jsonObject
            return att?.get("filename")?.jsonPrimitive?.contentOrNull ?: "attachment:$id"
        }

        val explicitVal = explicitOpt?.get("value")?.jsonPrimitive?.booleanOrNull
        val bundleName = attachmentLabel(bundleOpt)
        val chartName = attachmentLabel(chartOpt)
        val gameplayUrl = gameplayOpt?.get("value")?.jsonPrimitive?.contentOrNull

        println("Publish command received:")
        println(" - Bundle: $bundleName")
        println(" - Chart: $chartName")
        println(" - Gameplay URL: ${gameplayUrl ?: "N/A"}")
        println(" - Explicit: ${explicitVal ?: "N/A"}")

        // EphemeralMessage with embed structured message
        val json = ephemeralMessage {
            embed {
                title = I18n.t(locale, "publish_received")
                description = I18n.t(locale, "processing_not_implemented")
                field(I18n.t(locale, "bundle_label"), bundleName, inline = false)
                field(I18n.t(locale, "chart_label"), chartName, inline = false)
                gameplayUrl?.let { field(I18n.t(locale, "gameplay_url_label"), it, inline = false) }
                field(I18n.t(locale, "explicit_label"), explicitVal.toString(), inline = false)
                footer("bscm")
            }
        }

        call.respondJson(json)
    }
}
