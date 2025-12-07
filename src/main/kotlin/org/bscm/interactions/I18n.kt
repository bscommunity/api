package org.bscm.interactions

object I18n {
    private val translations: Map<String, Map<String, String>> = mapOf(
        "en" to mapOf(
            "pong" to "pong!",

            // Attachments
            "provide_required_attachments" to "Attach your chart bundle (.zip) to continue.",
            "missing_required_attachments" to "The chart bundle (.zip) is missing.",

            // Upload flow
            "publish_received" to "Upload received — starting the publishing process...",

            // Labels
            "bundle_label" to "Bundle",
            "chart_label" to "Chart",
            "gameplay_url_label" to "Gameplay URL",
            "explicit_label" to "Explicit",

            // Generic placeholders
            "processing_not_implemented" to "(Feature not available yet)",
            "null_label" to "(empty)",
            "missing_id_label" to "(missing id)",

            // Errors / unsupported
            "unsupported_interaction_type" to "This interaction type isn't supported.",
            "missing_data" to "Required data is missing.",
            "unsupported_command" to "This command isn’t supported.",
            "missing_user_context" to "Unable to identify your Discord user. Please try again.",

            // Account
            "account_required_title" to "Account Needed",
            "account_required_description" to "A **bscm** account is required to publish charts. Visit the dashboard to create one.",
            "account_create_button" to "Create an account",
            "next_steps_label" to "Next Steps",
            "create_account_steps" to "Create your account using the button below, then try publishing again.",

            // Download / persistence issues
            "bundle_download_failed" to "The bundle couldn’t be downloaded. Please try again.",
            "chart_persist_failed" to "The chart could not be saved. Please try again later.",

            // Success
            "publish_success_title" to "Chart Published! 🎉",
            "publish_success_description" to "Your chart is now live and available to the community. Manage it from the dashboard.",

            // Processing
            "processing_chart" to "Processing Chart",
            "processing_chart_description" to "Validating and preparing chart data... This may take a moment.",

            // Chart info labels
            "track_label" to "Track",
            "artist_label" to "Artist",
            "difficulty_label" to "Difficulty",
            "duration_label" to "Duration",
            "notes_label" to "Notes",
            "effects_label" to "Effects",
            "link_label" to "Link",

            // Footer info
            "manage_chart_info" to "Charts can be edited or removed from the dashboard at any time.",

            // Download step
            "downloading_bundle" to "Downloading Bundle",
            "downloading_bundle_description" to "Fetching your chart bundle data... It may take a moment.",

            // General errors
            "error" to "Error",
            "unexpected_error" to "An unexpected issue occurred during chart processing.",

            // Buttons
            "view_chart_button" to "View Chart",
            "open_dashboard_button" to "Open Dashboard"
        ),
        "pt_BR" to mapOf(
            "pong" to "pong!",

            // Attachments
            "provide_required_attachments" to "Envie o bundle (.zip) para continuar.",
            "missing_required_attachments" to "O bundle (.zip) não foi enviado.",

            // Upload flow
            "publish_received" to "Upload recebido — iniciando o processo de publicação...",

            // Labels
            "bundle_label" to "Bundle",
            "chart_label" to "Chart",
            "gameplay_url_label" to "URL de Gameplay",
            "explicit_label" to "Explicit",

            // Generic placeholders
            "processing_not_implemented" to "(Recurso ainda não disponível)",
            "null_label" to "(vazio)",
            "missing_id_label" to "(id ausente)",

            // Errors / unsupported
            "unsupported_interaction_type" to "Este tipo de interação não é suportado.",
            "missing_data" to "Dados necessários não foram encontrados.",
            "unsupported_command" to "Esse comando não é suportado.",
            "missing_user_context" to "Não foi possível identificar seu usuário do Discord. Tente novamente.",

            // Account
            "account_required_title" to "Conta Necessária",
            "account_required_description" to "Uma conta **bscm** é necessária para publicar charts. Acesse o dashboard para criar a sua.",
            "account_create_button" to "Criar conta",
            "next_steps_label" to "Próximos Passos",
            "create_account_steps" to "Crie sua conta usando o botão abaixo e tente publicar novamente.",

            // Download / persistence issues
            "bundle_download_failed" to "Não foi possível baixar o bundle. Tente novamente.",
            "chart_persist_failed" to "O chart não pôde ser salvo. Tente novamente mais tarde.",

            // Success
            "publish_success_title" to "Chart Publicado! 🎉",
            "publish_success_description" to "Seu chart está no ar e disponível para a comunidade. Gerencie tudo pelo dashboard.",

            // Processing
            "processing_chart" to "Processando chart",
            "processing_chart_description" to "Validando e preparando os dados do chart... Isso pode levar um momento.",

            // Chart info labels
            "track_label" to "Música",
            "artist_label" to "Artista",
            "difficulty_label" to "Dificuldade",
            "duration_label" to "Duração",
            "notes_label" to "Notas",
            "effects_label" to "Efeitos",
            "link_label" to "Link",

            // Footer info
            "manage_chart_info" to "Charts podem ser editados ou removidos do dashboard a qualquer momento.",

            // Download step
            "downloading_bundle" to "Baixando bundle",
            "downloading_bundle_description" to "Buscando os dados do seu bundle... Isso pode levar um momento.",

            // General errors
            "error" to "Erro",
            "unexpected_error" to "Um erro inesperado ocorreu durante o processamento do chart.",

            // Buttons
            "view_chart_button" to "Ver chart",
            "open_dashboard_button" to "Abrir dashboard"
        ),
        "es" to mapOf(
            "pong" to "pong!",

            // Attachments
            "provide_required_attachments" to "Adjunta el bundle (.zip) para continuar.",
            "missing_required_attachments" to "Falta el bundle (.zip).",

            // Upload flow
            "publish_received" to "Upload recibido — iniciando el proceso de publicación...",

            // Labels
            "bundle_label" to "Bundle",
            "chart_label" to "Chart",
            "gameplay_url_label" to "URL de Gameplay",
            "explicit_label" to "Explicit",

            // Generic placeholders
            "processing_not_implemented" to "(Función no disponible todavía)",
            "null_label" to "(vacío)",
            "missing_id_label" to "(id faltante)",

            // Errors / unsupported
            "unsupported_interaction_type" to "Este tipo de interacción no está soportado.",
            "missing_data" to "Faltan datos necesarios.",
            "unsupported_command" to "Este comando no está soportado.",
            "missing_user_context" to "No fue posible identificar tu usuario de Discord. Inténtalo de nuevo.",

            // Account
            "account_required_title" to "Cuenta Requerida",
            "account_required_description" to "Se necesita una cuenta de **bscm** para publicar charts. Entra al dashboard para crear una.",
            "account_create_button" to "Crear Cuenta",
            "next_steps_label" to "Próximos Pasos",
            "create_account_steps" to "Crea tu cuenta usando el botón de abajo y vuelve a intentar publicar.",

            // Download / persistence issues
            "bundle_download_failed" to "No se pudo descargar el bundle. Inténtalo de nuevo.",
            "chart_persist_failed" to "El chart no pudo guardarse. Inténtalo más tarde.",

            // Success
            "publish_success_title" to "¡Chart Publicado! 🎉",
            "publish_success_description" to "Tu chart ya está disponible para la comunidad. Puedes gestionarlo desde el dashboard.",

            // Processing
            "processing_chart" to "Procesando Chart",
            "processing_chart_description" to "Validando y preparando los datos del chart... Esto puede tomar un momento.",

            // Chart info labels
            "track_label" to "Música",
            "artist_label" to "Artista",
            "difficulty_label" to "Dificultad",
            "duration_label" to "Duración",
            "notes_label" to "Notas",
            "effects_label" to "Efectos",
            "link_label" to "Link",

            // Footer info
            "manage_chart_info" to "Los charts pueden editarse o eliminarse desde el dashboard en cualquier momento.",

            // Download step
            "downloading_bundle" to "Descargando Bundle",
            "downloading_bundle_description" to "Obteniendo los datos del bundle... Esto puede tomar un momento.",

            // General errors
            "error" to "Error",
            "unexpected_error" to "Ocurrió un problema inesperado durante el procesamiento del chart.",

            // Buttons
            "view_chart_button" to "Ver Chart",
            "open_dashboard_button" to "Abrir Dashboard"
        ),
        "ru" to mapOf(
            "pong" to "pong!",

            // Attachments
            "provide_required_attachments" to "Прикрепите bundle (.zip), чтобы продолжить.",
            "missing_required_attachments" to "Отсутствует bundle (.zip).",

            // Upload flow
            "publish_received" to "Файл получен — начинается публикация...",

            // Labels
            "bundle_label" to "Bundle",
            "chart_label" to "Chart",
            "gameplay_url_label" to "URL геймплея",
            "explicit_label" to "Explicit",

            // Generic placeholders
            "processing_not_implemented" to "(Функция ещё не доступна)",
            "null_label" to "(пусто)",
            "missing_id_label" to "(id отсутствует)",

            // Errors / unsupported
            "unsupported_interaction_type" to "Этот тип взаимодействия не поддерживается.",
            "missing_data" to "Отсутствуют необходимые данные.",
            "unsupported_command" to "Эта команда не поддерживается.",
            "missing_user_context" to "Не удалось определить ваш Discord-пользователь. Попробуйте снова.",

            // Account
            "account_required_title" to "Требуется аккаунт",
            "account_required_description" to "Для публикации charts требуется аккаунт **bscm**. Создайте его на dashboard.",
            "account_create_button" to "Создать аккаунт",
            "next_steps_label" to "Следующие шаги",
            "create_account_steps" to "Создайте аккаунт с помощью кнопки ниже и попробуйте снова.",

            // Download / persistence issues
            "bundle_download_failed" to "Не удалось скачать bundle. Попробуйте ещё раз.",
            "chart_persist_failed" to "Chart не удалось сохранить. Попробуйте позже.",

            // Success
            "publish_success_title" to "Chart опубликован! 🎉",
            "publish_success_description" to "Ваш chart теперь доступен сообществу. Управление доступно через dashboard.",

            // Processing
            "processing_chart" to "Обработка Chart",
            "processing_chart_description" to "Проверка и подготовка данных chart... Это может занять немного времени.",

            // Chart info labels
            "track_label" to "Трек",
            "artist_label" to "Исполнитель",
            "difficulty_label" to "Сложность",
            "duration_label" to "Длительность",
            "notes_label" to "Ноты",
            "effects_label" to "Эффекты",
            "link_label" to "Ссылка",

            // Footer info
            "manage_chart_info" to "Charts можно редактировать или удалять через dashboard в любое время.",

            // Download step
            "downloading_bundle" to "Скачивание Bundle",
            "downloading_bundle_description" to "Получение данных bundle... Это может занять немного времени.",

            // General errors
            "error" to "Ошибка",
            "unexpected_error" to "Произошла непредвиденная ошибка при обработке chart.",

            // Buttons
            "view_chart_button" to "Открыть Chart",
            "open_dashboard_button" to "Открыть Dashboard"
        ),
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

