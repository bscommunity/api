package org.bscm.services

import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import org.bscm.models.dto.chart.CreateChartRequest
import org.bscm.plugins.applicationHttpClient
import org.bscm.plugins.jsonClient

class UploadService(
    private val webhookUrl: String,
) {
    @Serializable
    data class DiscordMessageResponse(val id: String)

    private fun buildWebhookPayload(chart: CreateChartRequest): String {
        val durationFormatted = String.format("%d:%02d", (chart.duration / 60).toInt(), (chart.duration % 60).toInt())

        val fields = listOf(
            EmbedField("🎚️ Difficulty", chart.difficulty.name, true),
            EmbedField("💿 Deluxe", if (chart.isDeluxe) "Yes" else "No", true),
            EmbedField("⚠️ Explicit", if (chart.isExplicit) "Yes" else "No", true),
            EmbedField("🕒 Duration", durationFormatted, true),
            EmbedField("🎵 Notes", chart.notesAmount.toString(), true),
            EmbedField("✨ Effects", chart.effectsAmount.toString(), true),
            EmbedField("🎶 BPM", chart.bpm.toString(), true),
        )

        val embed = WebhookEmbed(
            title = "${chart.track} – ${chart.artist}",
            // description = "_A new chart has just landed!_",
            color = 0x1DB954, // green
            thumbnail = Thumbnail(chart.coverUrl),
            fields = fields,
            footer = Footer("Uploaded via bscm", icon_url = "https://imgur.com/7e4lzGf.png"),
        )

        val payload = WebhookPayload(
            username = "bscm",
            embeds = listOf(embed)
        )

        println(jsonClient.encodeToString(WebhookPayload.serializer(), payload))

        return jsonClient.encodeToString(WebhookPayload.serializer(), payload)
    }

    suspend fun uploadChart(chart: CreateChartRequest, chartBundle: ByteArray): DiscordMessageResponse {
        val payloadJson = buildWebhookPayload(chart)

        val response: HttpResponse = applicationHttpClient.submitFormWithBinaryData(
            url = webhookUrl,
            formData = formData {
                // The message content
                append(
                    "payload_json", payloadJson, Headers.build {
                        append(HttpHeaders.ContentType, "application/json")
                    }
                )
                append("file", chartBundle, Headers.build {
                    append(HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"chart.zip\"")
                    append(HttpHeaders.ContentType, ContentType.Application.Zip.toString())
                })
            }
        )

        if (!response.status.isSuccess()) {
            throw Exception("Failed to send: ${response.status}, ${response.bodyAsText()}")
        }

        println(response.bodyAsText())

        return jsonClient.decodeFromString(DiscordMessageResponse.serializer(), response.bodyAsText())
    }
}

@Serializable
data class WebhookPayload(
    val username: String = "bscm",
    val avatar_url: String? = null,
    val embeds: List<WebhookEmbed>
)

@Serializable
data class WebhookEmbed(
    val title: String,
    val description: String? = null,
    val url: String? = null,
    val color: Int,
    val thumbnail: Thumbnail,
    val fields: List<EmbedField>,
    val footer: Footer? = null,
    // val timestamp: String
)

@Serializable
data class Thumbnail(val url: String)

@Serializable
data class EmbedField(
    val name: String,
    val value: String,
    val inline: Boolean = true
)

@Serializable
data class Footer(
    val text: String,
    val icon_url: String? = null
)

