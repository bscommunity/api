package org.bscm.interactions

object I18n {
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

    fun normalizeLocale(locale: String?): String {
        if (locale == null) return "en"
        val lower = locale.lowercase()
        return when {
            lower.startsWith("pt") -> "pt_BR"
            lower.startsWith("es") -> "es"
            lower.startsWith("ru") -> "ru"
            else -> "en"
        }
    }

    fun t(locale: String?, key: String): String {
        val norm = normalizeLocale(locale)
        return translations[norm]?.get(key)
            ?: translations["en"]?.get(key)
            ?: key
    }
}

