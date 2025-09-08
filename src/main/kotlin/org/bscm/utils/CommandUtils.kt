package org.bscm.utils

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

@Serializable
data class CommandOption(
    val type: Int, // 3=STRING 5=BOOLEAN 11=ATTACHMENT
    val name: String,
    val description: String,
    val required: Boolean = false
)

@Serializable
data class ApplicationCommand(
    val name: String,
    val description: String,
    val options: List<CommandOption>? = null
)

fun registerDiscordCommands(botToken: String, appId: String, guildId: String? = null) {
    val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }

    val commands = listOf(
        ApplicationCommand(
            name = "ping",
            description = "Replies with pong!"
        ),
        ApplicationCommand(
            name = "publish",
            description = "Publishes a new chart (attachments required)",
            options = listOf(
                CommandOption(11, "bundle_zip", "Bundle .zip (attachment)", true),
                CommandOption(11, "chart_file", ".chart file (attachment)", true),
                CommandOption(3, "gameplay_url", "Gameplay URL (YouTube)", false),
                CommandOption(5, "is_explicit", "Explicit?", false)
            )
        )
    )

    val scope = if (guildId.isNullOrBlank()) "global" else "guild:$guildId"
    val url = if (guildId.isNullOrBlank())
        "https://discord.com/api/v10/applications/$appId/commands"
    else
        "https://discord.com/api/v10/applications/$appId/guilds/$guildId/commands"

    runBlocking {
        try {
            val response = client.put(url) {
                header(HttpHeaders.Authorization, "Bot $botToken")
                contentType(ContentType.Application.Json)
                setBody(commands)
            }
            println("[DiscordCmd][$scope] Status: ${response.status}")
            println("[DiscordCmd][$scope] Body: ${response.bodyAsText()}")
        } catch (e: Exception) {
            println("[DiscordCmd][$scope] Erro: $e")
        }
    }
}

fun clearDiscordCommands(botToken: String, appId: String, guildId: String? = null) {
    val client = HttpClient(CIO) { install(ContentNegotiation) { json() } }
    val scope = if (guildId.isNullOrBlank()) "global" else "guild:$guildId"
    val url = if (guildId.isNullOrBlank())
        "https://discord.com/api/v10/applications/$appId/commands"
    else
        "https://discord.com/api/v10/applications/$appId/guilds/$guildId/commands"

    runBlocking {
        try {
            /*val response = client.put(url) {
                header(HttpHeaders.Authorization, "Bot $botToken")
                contentType(ContentType.Application.Json)
                setBody(emptyList<ApplicationCommand>())
            }*/
            val response = client.get(url) {
                header(HttpHeaders.Authorization, "Bot $botToken")
            }
            println("[DiscordCmd][CLEAR][$scope] Status: ${response.status}")
            println("[DiscordCmd][CLEAR][$scope] Body: ${response.bodyAsText()}")
        } catch (e: Exception) {
            println("[DiscordCmd][CLEAR][$scope] Erro: $e")
        }
    }
}