package org.bscm.interactions.commands

import io.ktor.server.application.*
import kotlinx.serialization.json.*
import org.bscm.interactions.I18n
import org.bscm.interactions.ephemeralMessage
import org.bscm.interactions.respondJson

object PublishCommand {
    suspend fun handle(call: ApplicationCall, data: JsonObject, locale: String?) {
        val optionsArray = data["options"]?.jsonArray
        if (optionsArray == null || optionsArray.isEmpty()) {
            call.respondJson(ephemeralMessage(I18n.t(locale, "provide_required_attachments")))
            return
        }

        val options = optionsArray.mapNotNull { it.jsonObject }
        fun findOption(n: String) = options.firstOrNull { it["name"]?.jsonPrimitive?.content == n }?.jsonObject

        val bundleOpt = findOption("bundle_zip")
        val chartOpt = findOption("chart_file")
        val gameplayOpt = findOption("gameplay_url")
        val explicitOpt = findOption("is_explicit")

        if (bundleOpt == null || chartOpt == null) {
            call.respondJson(ephemeralMessage(I18n.t(locale, "missing_required_attachments")))
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

        val content = buildString {
            append(I18n.t(locale, "publish_received")).append('\n')
            append(I18n.t(locale, "bundle_label")).append(' ').append(attachmentLabel(bundleOpt)).append('\n')
            append(I18n.t(locale, "chart_label")).append(' ').append(attachmentLabel(chartOpt)).append('\n')
            gameplayOpt?.get("value")?.jsonPrimitive?.contentOrNull?.let { append(I18n.t(locale, "gameplay_url_label")).append(' ').append(it).append('\n') }
            append(I18n.t(locale, "explicit_label")).append(' ').append(explicitVal).append('\n')
            append(I18n.t(locale, "processing_not_implemented"))
        }

        call.respondJson(ephemeralMessage(content))
    }
}
