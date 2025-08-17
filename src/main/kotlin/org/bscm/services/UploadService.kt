package org.bscm.services

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.bscm.models.Chart
import org.bscm.models.StreamingLink
import org.bscm.models.User
import org.bscm.models.Version
import org.bscm.models.dto.chart.CreateChartRequest
import org.bscm.models.dto.version.CreateVersionRequest
import org.bscm.models.enums.Difficulty
import org.bscm.models.enums.StreamingPlatform
import org.bscm.plugins.applicationHttpClient
import org.bscm.plugins.jsonClient
import java.util.*

class UploadService(
    private val webhookId: String,
    private val webhookToken: String,
    private val botToken: String,
    private val channelId: String,
) {
    private val webhookUrl = "https://discord.com/api/webhooks/$webhookId/$webhookToken"
    private val editWebhookUrl = "https://discord.com/api/v10/webhooks/$webhookId/$webhookToken/messages"

    private val hardIcon = "<:hard:1393411882282385458>"
    private val extremeIcon = "<:extreme:1393411880067797115>"
    private val deluxeIcon = "<:deluxe:1393402180991586365>"
    private val explicitIcon = "<:explicit:1393411886690586705>"

    private val durationIcon = "<:duration:1393800289940672635>"
    private val noteIcon = "<:note:1393800294030114919>"
    private val effectIcon = "<:effect:1393800291719053392>"
    private val downloadIcon = "<:download:1393800288271335525>"
    private val lastUpdatedIcon = "<:last_updated:1393800286639886496>"

    @Serializable
    data class DiscordMessageResponse(
        val id: String,
        @SerialName("channel_id") val channelId: String,
        val attachments: List<Attachment>,
    )

    private fun getNormalizedTrackName(track: String): String {
        val normalized = track.trim()
            .lowercase(Locale.getDefault())
            .replace(Regex("\\s*\\([^)]*\\)"), "")   // remove (...)
            .replace(Regex("\\s*\\[.*?]"), "")       // remove [...]
            .replace(Regex("\\s*[Ff]eat\\..*"), "")  // remove feat...
            .replace(Regex("[^a-zA-Z0-9 ]"), " ")    // replace non-alfa numeric with space
            .replace(Regex("\\s+"), "_")             // collapse whitespace → underscore
            .trim('_')                               // trim leading/trailing underscores
        return normalized
    }

    private fun getButtonForPlatform(platform: StreamingPlatform, url: String): Button {
        return when (platform) {
            StreamingPlatform.SPOTIFY -> Button(
                type = 2,
                style = 5,
                label = "Spotify",
                emoji = Emoji("1393805551602892860", "spotify", false),
                url = url,
                // customId = "spotify_button"
            )

            StreamingPlatform.APPLE_MUSIC -> Button(
                type = 2,
                style = 5,
                label = "Apple Music",
                emoji = Emoji("1393805548180344923", "itunes", false),
                url = url,
                // customId = "apple_music_button"
            )

            StreamingPlatform.YOUTUBE_MUSIC -> Button(
                type = 2,
                style = 5,
                label = "YouTube Music",
                emoji = Emoji("1393805555457589301", "unknown", false),
                url = url,
                // customId = "youtube_music_button"
            )

            StreamingPlatform.TIDAL -> Button(
                type = 2,
                style = 5,
                label = "Tidal",
                emoji = Emoji("1393805553297522841", "tidal", false),
                url = url,
                // customId = "tidal_button"
            )

            StreamingPlatform.DEEZER -> Button(
                type = 2,
                style = 5,
                label = "Deezer",
                emoji = Emoji("1393805549707071588", "deezer", false),
                url = url,
                // customId = "deezer_button"
            )

            StreamingPlatform.AMAZON_MUSIC -> Button(
                type = 2,
                style = 5,
                label = "Amazon Music",
                emoji = Emoji("1394147798772879371", "amazon_music", false),
                url = url,
                // customId = "amazon_music"
            )

            StreamingPlatform.SOUNDCLOUD -> Button(
                type = 2,
                style = 5,
                label = "Soundcloud",
                emoji = Emoji("1394147704598302760", "soundcloud", false),
                url = url,
                // customId = "soundcloud_button"
            )

            else -> Button(
                type = 2,
                style = 5,
                label = platform.name,
                url = url,
            )
        }
    }

    private fun buildComponents(trackUrls: List<StreamingLink>): List<ActionRow> {
        if (trackUrls.isEmpty()) return emptyList()

        val buttons = trackUrls.map { streamingLink ->
            getButtonForPlatform(streamingLink.platform, streamingLink.url)
        }

        // Organize buttons into rows of up to 3 buttons each
        return buttons.chunked(3).map { buttonGroup ->
            ActionRow(type = 1, components = buttonGroup)
        }
    }

    private fun buildWebhookPayload(
        chart: CreateChartRequest,
        author: User,
        attachments: List<BaseAttachment> = emptyList()
    ): String {
        val durationFormatted =
            String.format("%dm%ds", (chart.duration / 60).toInt(), (chart.duration % 60).toInt())

        // Build title with icons based on chart properties
        val titleIcons = buildString {
            when (chart.difficulty) {
                Difficulty.HARD -> append(" $hardIcon")
                Difficulty.EXTREME -> append(" $extremeIcon")
                else -> {} // No icon for NORMAL and EXPERT
            }
            if (chart.isDeluxe) append(" $deluxeIcon")
            if (chart.isExplicit) append(" $explicitIcon")
        }

        val title = "${chart.track} – ${chart.artist}$titleIcons"

        val fields = listOf(
            EmbedField("Duration", "$durationIcon $durationFormatted", true),
            EmbedField("Notes Amount", "$noteIcon ${chart.notesAmount} notes", true),
            EmbedField("Effects Amount", "$effectIcon ${chart.effectsAmount} effects", false),
        )

        val embed = WebhookEmbed(
            title = title,
            // Omitir description completamente
            url = "https://bscm.netlify.app/link/chart/${chart.shareId}",
            timestamp = Date().toInstant().toString(),
            color = 3820816,
            thumbnail = Thumbnail(""),
            image = Image(chart.coverUrl),
            author = Author("New chart submitted"),
            fields = fields,
            footer = Footer(
                text = "Submitted by @${author.username}",
                iconUrl = author.imageUrl
            )
        )

        val components = buildComponents(chart.trackUrls)

        val payload = WebhookPayload(
            username = "bscm",
            avatarUrl = "https://i.imgur.com/7e4lzGf.png",
            embeds = listOf(embed),
            attachments = attachments,
            components = components
        )

        println(jsonClient.encodeToString(WebhookPayload.serializer(), payload))

        return jsonClient.encodeToString(WebhookPayload.serializer(), payload)
    }

    suspend fun uploadChart(chart: CreateChartRequest, author: User, chartBundle: ByteArray): DiscordMessageResponse {
        val payloadJson = buildWebhookPayload(chart, author)
        val normalizedTrack = getNormalizedTrackName(chart.track)

        val response: HttpResponse = applicationHttpClient.submitFormWithBinaryData(
            url = "${webhookUrl}?with_components=true",
            formData = formData {
                // The message content
                append("payload_json", payloadJson, Headers.build {
                    append(HttpHeaders.ContentType, "application/json")
                })
                append("file", chartBundle, Headers.build {
                    append(
                        HttpHeaders.ContentDisposition,
                        "form-data; name=\"file\"; filename=\"${normalizedTrack}_v1.zip\""
                    )
                    append(HttpHeaders.ContentType, ContentType.Application.Zip.toString())
                })
            }
        )

        if (!response.status.isSuccess()) {
            throw Exception("Failed to send: ${response.status}, ${response.bodyAsText()}")
        }

        println(jsonClient.decodeFromString(DiscordMessageResponse.serializer(), response.bodyAsText()))

        return jsonClient.decodeFromString(DiscordMessageResponse.serializer(), response.bodyAsText())
    }

    @OptIn(InternalAPI::class)
    suspend fun uploadVersion(
        messageId: String,
        chart: Chart,
        newVersion: CreateVersionRequest,
        author: User,
        chartBundle: ByteArray,
    ): DiscordMessageResponse {
        val latestVersionIndex = chart.versions.maxOfOrNull { it.index } ?: 1
        val newIndex = latestVersionIndex + 1

        val normalizedTrack = getNormalizedTrackName(chart.track)

        // We need to update the displayed info with the new version data
        val payloadJson = buildWebhookPayload(
            CreateChartRequest(
                track = newVersion.track,
                artist = newVersion.artist,
                duration = newVersion.duration,
                notesAmount = newVersion.notesAmount,
                effectsAmount = newVersion.effectsAmount,
                difficulty = newVersion.difficulty,
                isDeluxe = newVersion.isDeluxe,
                isExplicit = newVersion.isExplicit,
                trackPreviewUrl = chart.trackPreviewUrl,
                bpm = newVersion.bpm,
                bundleUrl = newVersion.bundleUrl,
                previewUrl = newVersion.previewUrl,
                coverUrl = chart.coverUrl,
                trackUrls = chart.trackUrls,
                shareId = chart.shareId,
            ),
            author,
            attachments = chart.versions.map {
                SimpleAttachment(
                    id = it.id,
                    filename = "${normalizedTrack}_v${it.index}.zip",
                )
            })

        // println("Current message attachments: $payloadJson")

        val response: HttpResponse = applicationHttpClient.submitFormWithBinaryData(
            url = "${editWebhookUrl}/${messageId}?with_components=true",
            formData = formData {
                append("payload_json", payloadJson, Headers.build {
                    append(HttpHeaders.ContentType, "application/json")
                })
                append("file", chartBundle, Headers.build {
                    append(
                        HttpHeaders.ContentDisposition,
                        "form-data; name=\"file\"; filename=\"chart_v${newIndex}.zip\""
                    )
                    append(HttpHeaders.ContentType, ContentType.Application.Zip.toString())
                })
            }
        ) {
            method = HttpMethod.Patch
        }

        if (!response.status.isSuccess()) {
            throw Exception("Failed to send: ${response.status}, ${response.bodyAsText()}")
        }

        // println(jsonClient.decodeFromString(DiscordMessageResponse.serializer(), response.bodyAsText()))

        return jsonClient.decodeFromString(DiscordMessageResponse.serializer(), response.bodyAsText())
    }

    suspend fun deleteVersion(messageId: String, track: String, versions: List<Version>, versionId: String): Boolean {
        val remainingVersions = versions.filter { it.id != versionId }
        val normalizedTrack = getNormalizedTrackName(track)

        val payloadJson = jsonClient.encodeToString(
            SimpleWebhookPayload.serializer(), SimpleWebhookPayload(
                attachments = remainingVersions.map {
                    SimpleAttachment(
                        id = it.id,
                        filename = "${normalizedTrack}_v${it.index}.zip",
                    )
                },
            )
        )

        println("Current message attachments after deletion: $payloadJson")

        val response: HttpResponse = applicationHttpClient.submitFormWithBinaryData(
            url = "${editWebhookUrl}/${messageId}?with_components=true",
            formData = formData {
                append("payload_json", payloadJson, Headers.build {
                    append(HttpHeaders.ContentType, "application/json")
                })
            }
        ) {
            method = HttpMethod.Patch
        }

        if (!response.status.isSuccess()) {
            throw Exception("Failed to delete: ${response.status}, ${response.bodyAsText()}")
        }

        return response.status == HttpStatusCode.NoContent || response.status == HttpStatusCode.OK
    }

    suspend fun deleteMessage(messageId: String): Boolean {
        val response: HttpResponse = applicationHttpClient.delete("$editWebhookUrl/$messageId")

        if (!response.status.isSuccess()) {
            throw Exception("Failed to delete message: ${response.status}, ${response.bodyAsText()}")
        }

        return response.status == HttpStatusCode.NoContent || response.status == HttpStatusCode.OK
    }

    // Refreshes all bundle URLs in the webhook messages (from the charts channel)
    suspend fun refreshBundleUrls(): Map<String, String> {
        val maxMessages = 1000
        val messagesPerRequest = 100
        val bundleUrls = mutableMapOf<String, String>()
        var lastMessageId: String? = null
        var fetched = 0
        while (fetched < maxMessages) {
            val url = "https://discord.com/api/v10/channels/$channelId/messages?limit=$messagesPerRequest" +
                (lastMessageId?.let { "&before=$it" } ?: "")
            val response: HttpResponse = applicationHttpClient.get(url) {
                header(HttpHeaders.Authorization, "Bot $botToken")
            }

            // Rate limit handling
            val rateLimitRemaining = response.headers["X-RateLimit-Remaining"]?.toIntOrNull() ?: 1
            val rateLimitResetAfter = response.headers["X-RateLimit-Reset-After"]?.toDoubleOrNull() ?: 0.0
            if (response.status.value == 429) {
                val retryAfter = response.headers["Retry-After"]?.toDoubleOrNull() ?: rateLimitResetAfter
                delay((retryAfter * 1000).toLong())
                continue
            } else if (rateLimitRemaining == 0) {
                delay((rateLimitResetAfter * 1000).toLong())
            }

            val messagesJson = response.bodyAsText()
            val messages = jsonClient.decodeFromString(
                JsonArray.serializer(), messagesJson
            )

            if (messages.isEmpty()) break

            for (msg in messages) {
                val obj = msg.jsonObject
                val attachments = obj["attachments"]?.jsonArray
                if (attachments != null && attachments.isNotEmpty()) {
                    val lastAttachment = attachments.last().jsonObject
                    val url = lastAttachment["url"]?.jsonPrimitive?.content
                    val id = lastAttachment["id"]?.jsonPrimitive?.content
                    if (id != null && url != null) {
                        bundleUrls[id] = url
                    }
                }
            }

            lastMessageId = messages.last().jsonObject["id"]?.jsonPrimitive?.content
            fetched += messages.size

            if (messages.size < messagesPerRequest) break
        }
        return bundleUrls
    }
}

@Serializable
data class WebhookPayload(
    val username: String = "bscm",
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val embeds: List<WebhookEmbed>,
    val attachments: List<BaseAttachment> = emptyList(),
    val components: List<ActionRow> = emptyList()
)

@Serializable
data class WebhookEmbed(
    val title: String,
    val description: String? = null,
    val url: String? = null,
    val color: Int,
    val thumbnail: Thumbnail,
    val image: Image?,
    val author: Author? = null,
    val fields: List<EmbedField>,
    val footer: Footer? = null,
    val timestamp: String? = null
)

@Serializable
data class Thumbnail(val url: String)

@Serializable
data class Image(val url: String)

@Serializable
data class Author(val name: String, val url: String? = null)

@Serializable
data class EmbedField(
    val name: String,
    val value: String,
    val inline: Boolean
)

@Serializable
data class Footer(
    val text: String,
    @SerialName("icon_url") val iconUrl: String? = null
)

@Serializable
data class ActionRow(
    val type: Int,
    val components: List<Button>
)

@Serializable
data class Button(
    val type: Int,
    val style: Int,
    val label: String,
    val emoji: Emoji? = null,
    val url: String? = null,
    // @SerialName("custom_id") val customId: String? = null
)

@Serializable
data class Emoji(
    val id: String,
    val name: String,
    val animated: Boolean
)

@Serializable
sealed interface BaseAttachment

@Serializable
data class Attachment(
    val id: String,
    val filename: String,
    val url: String,
    @SerialName("proxy_url") val proxyUrl: String,
    val size: Int,
    val height: Int? = null,
    val width: Int? = null
) : BaseAttachment

@Serializable
data class SimpleAttachment(
    val id: String,
    val filename: String,
) : BaseAttachment

@Serializable
data class SimpleWebhookPayload(
    val attachments: List<SimpleAttachment>,
)

