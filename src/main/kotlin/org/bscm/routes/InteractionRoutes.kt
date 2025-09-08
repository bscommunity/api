package org.bscm.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*
import org.bscm.utils.verifyEd25519

// Extensions & helpers + i18n
private val translations: Map<String, Map<String, String>> = mapOf(
    "en" to mapOf(
        "pong" to "pong!",
        "provide_required_attachments" to "Provide the required attachments: bundle_zip (.zip) and chart_file (.chart).",
        "missing_required_attachments" to "Missing required attachments: bundle_zip and chart_file.",
        "publish_received" to "Received /publish.",
        "bundle_label" to "Bundle:",
        "chart_label" to "Chart:",
        "gameplay_url_label" to "Gameplay URL:",
        "explicit_label" to "Explicit:",
        "processing_not_implemented" to "(Further processing not implemented yet)",
        "null_label" to "(null)",
        "missing_id_label" to "(missing id)",
        "unsupported_interaction_type" to "unsupported interaction type",
        "missing_data" to "missing data",
        "unsupported_command" to "unsupported command"
    ),
    "pt_BR" to mapOf(
        "pong" to "pong!",
        "provide_required_attachments" to "Forneça os anexos obrigatórios: bundle_zip (.zip) e chart_file (.chart).",
        "missing_required_attachments" to "Anexos obrigatórios ausentes: bundle_zip e chart_file.",
        "publish_received" to "Recebido /publish.",
        "bundle_label" to "Bundle:",
        "chart_label" to "Chart:",
        "gameplay_url_label" to "Gameplay URL:",
        "explicit_label" to "Explícito:",
        "processing_not_implemented" to "(Processamento posterior ainda não implementado)",
        "null_label" to "(nulo)",
        "missing_id_label" to "(id ausente)",
        "unsupported_interaction_type" to "tipo de interação não suportado",
        "missing_data" to "dados ausentes",
        "unsupported_command" to "comando não suportado"
    ),
    "es" to mapOf(
        "pong" to "pong!",
        "provide_required_attachments" to "Proporcione los adjuntos requeridos: bundle_zip (.zip) y chart_file (.chart).",
        "missing_required_attachments" to "Faltan adjuntos requeridos: bundle_zip y chart_file.",
        "publish_received" to "Recibido /publish.",
        "bundle_label" to "Bundle:",
        "chart_label" to "Chart:",
        "gameplay_url_label" to "URL de Gameplay:",
        "explicit_label" to "Explícito:",
        "processing_not_implemented" to "(Procesamiento adicional aún no implementado)",
        "null_label" to "(nulo)",
        "missing_id_label" to "(id faltante)",
        "unsupported_interaction_type" to "tipo de interacción no soportado",
        "missing_data" to "datos faltantes",
        "unsupported_command" to "comando no soportado"
    ),
    "ru" to mapOf(
        "pong" to "pong!",
        "provide_required_attachments" to "Предоставьте обязательные вложения: bundle_zip (.zip) и chart_file (.chart).",
        "missing_required_attachments" to "Отсутствуют обязательные вложения: bundle_zip и chart_file.",
        "publish_received" to "Получена /publish.",
        "bundle_label" to "Bundle:",
        "chart_label" to "Chart:",
        "gameplay_url_label" to "URL геймплея:",
        "explicit_label" to "Explicit:",
        "processing_not_implemented" to "(Дальнейшая обработка ещё не реализована)",
        "null_label" to "(null)",
        "missing_id_label" to "(id отсутствует)",
        "unsupported_interaction_type" to "неподдерживаемый тип взаимодействия",
        "missing_data" to "отсутствуют данные",
        "unsupported_command" to "неподдерживаемая команда"
    )
)

private fun normalizeLocale(locale: String?): String {
    if (locale == null) return "en"
    val lower = locale.lowercase()
    return when {
        lower.startsWith("pt") -> "pt_BR"
        lower.startsWith("es") -> "es"
        lower.startsWith("ru") -> "ru"
        else -> "en"
    }
}

private fun t(locale: String?, key: String): String {
    val norm = normalizeLocale(locale)
    return translations[norm]?.get(key)
        ?: translations["en"]?.get(key)
        ?: key
}

private suspend fun io.ktor.server.application.ApplicationCall.respondJson(obj: JsonObject) {
    respond(HttpStatusCode.OK, obj)
}

private fun ephemeralMessage(content: String): JsonObject = buildJsonObject {
    put("type", 4)
    put("data", buildJsonObject {
        put("flags", 64)
        put("content", content)
    })
}

private fun interactionCallbackMessage(content: String): JsonObject = ephemeralMessage(content)

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

        when (json["type"]?.jsonPrimitive?.intOrNull) {
            1 -> call.respond(HttpStatusCode.OK, buildJsonObject { put("type", 1) }) // PING
            2 -> call.handleApplicationCommand(json) // APPLICATION_COMMAND
            else -> call.respond(HttpStatusCode.NotImplemented, t(json["locale"]?.jsonPrimitive?.contentOrNull, "unsupported_interaction_type"))
        }
    }
}

// /publish command logic inline
private suspend fun io.ktor.server.application.ApplicationCall.handleApplicationCommand(payload: JsonObject) {
    val data = payload["data"]?.jsonObject ?: return respond(HttpStatusCode.BadRequest, t(payload["locale"]?.jsonPrimitive?.contentOrNull, "missing_data"))
    val name = data["name"]?.jsonPrimitive?.contentOrNull
    val locale = payload["locale"]?.jsonPrimitive?.contentOrNull ?: payload["guild_locale"]?.jsonPrimitive?.contentOrNull

    when (name) {
        "ping" -> {
            respondJson(interactionCallbackMessage(t(locale, "pong")))
            return
        }
        "publish" -> {
            // continue flow
        }
        else -> {
            respond(HttpStatusCode.NotImplemented, t(locale, "unsupported_command"))
            return
        }
    }

    val optionsArray = data["options"]?.jsonArray
    if (optionsArray == null || optionsArray.isEmpty()) {
        respondJson(ephemeralMessage(t(locale, "provide_required_attachments")))
        return
    }

    val options = optionsArray.mapNotNull { it.jsonObject }
    fun findOption(n: String) = options.firstOrNull { it["name"]?.jsonPrimitive?.content == n }?.jsonObject

    val bundleOpt = findOption("bundle_zip")
    val chartOpt = findOption("chart_file")
    val gameplayOpt = findOption("gameplay_url")
    val explicitOpt = findOption("is_explicit")

    if (bundleOpt == null || chartOpt == null) {
        respondJson(ephemeralMessage(t(locale, "missing_required_attachments")))
        return
    }

    val resolved = data["resolved"]?.jsonObject
    val attachments = resolved?.get("attachments")?.jsonObject
    fun attachmentLabel(opt: JsonObject?): String {
        if (opt == null) return t(locale, "null_label")
        val id = opt["value"]?.jsonPrimitive?.contentOrNull ?: return t(locale, "missing_id_label")
        val att = attachments?.get(id)?.jsonObject
        return att?.get("filename")?.jsonPrimitive?.contentOrNull ?: "attachment:$id"
    }

    val explicitVal = explicitOpt?.get("value")?.jsonPrimitive?.booleanOrNull

    val content = buildString {
        append(t(locale, "publish_received")).append('\n')
        append(t(locale, "bundle_label")).append(' ').append(attachmentLabel(bundleOpt)).append('\n')
        append(t(locale, "chart_label")).append(' ').append(attachmentLabel(chartOpt)).append('\n')
        gameplayOpt?.get("value")?.jsonPrimitive?.contentOrNull?.let { append(t(locale, "gameplay_url_label")).append(' ').append(it).append('\n') }
        append(t(locale, "explicit_label")).append(' ').append(explicitVal).append('\n')
        append(t(locale, "processing_not_implemented"))
    }

    respondJson(interactionCallbackMessage(content))
}