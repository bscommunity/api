package org.bscm.services

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bscm.interactions.*
import org.bscm.models.Chart
import org.bscm.models.StreamingRef
import org.bscm.models.User
import org.bscm.models.Version
import org.bscm.models.dto.chart.CreateChartRequest
import org.bscm.models.dto.version.CreateVersionRequest
import org.bscm.models.enums.Difficulty
import org.bscm.models.enums.StreamingPlatform
import org.bscm.services.track.clients.applicationHttpClient
import org.bscm.services.track.clients.jsonClient
import java.util.*

private val logger = KtorSimpleLogger("UploadService")

class UploadService(
    private val webhookId: String,
    private val webhookToken: String,
    private val botToken: String,
    private val channelId: String,
) {
    private val workshopUsername = "bscm"
    private val workshopAvatarUrl = "https://i.imgur.com/7e4lzGf.png"

    data class SubmittedBy(
        val username: String,
        val avatarUrl: String?,
    ) {
        companion object {
            fun fromUser(user: User): SubmittedBy = SubmittedBy(
                username = user.username,
                avatarUrl = user.avatarUrl,
            )
        }
    }

    data class PublishContext(
        val contentId: String? = null,
        val submittedBy: SubmittedBy,
        val trackUrls: List<StreamingRef> = emptyList(),
    )

    data class TourPassPublishData(
        val title: String,
        val description: String?,
        val context: PublishContext,
        val coverUrl: String?,
        val durationSeconds: Int,
        val tracksAmount: Int,
        val difficultyLabel: String? = null,
        val trailerUrl: String? = null,
        val tracklist: List<String> = emptyList(),
    )

    data class ThemePublishData(
        val title: String,
        val description: String?,
        val context: PublishContext,
        val replaces: String,
        val trailerUrl: String?,
        val coverArtUrl: String?,
        val displayArtUrl: String?,
    )

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
        val embeds: List<Embed>,
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
            )

            StreamingPlatform.APPLE_MUSIC -> Button(
                type = 2,
                style = 5,
                label = "Apple Music",
                emoji = Emoji("1393805548180344923", "itunes", false),
                url = url,
            )

            StreamingPlatform.YOUTUBE_MUSIC -> Button(
                type = 2,
                style = 5,
                label = "YouTube Music",
                emoji = Emoji("1393805555457589301", "unknown", false),
                url = url,
            )

            StreamingPlatform.TIDAL -> Button(
                type = 2,
                style = 5,
                label = "Tidal",
                emoji = Emoji("1393805553297522841", "tidal", false),
                url = url,
            )

            StreamingPlatform.DEEZER -> Button(
                type = 2,
                style = 5,
                label = "Deezer",
                emoji = Emoji("1393805549707071588", "deezer", false),
                url = url,
            )

            StreamingPlatform.AMAZON_MUSIC -> Button(
                type = 2,
                style = 5,
                label = "Amazon Music",
                emoji = Emoji("1394147798772879371", "amazon_music", false),
                url = url,
            )

            StreamingPlatform.SOUNDCLOUD -> Button(
                type = 2,
                style = 5,
                label = "Soundcloud",
                emoji = Emoji("1394147704598302760", "soundcloud", false),
                url = url,
            )

            StreamingPlatform.LAST_FM -> Button(
                type = 2,
                style = 5,
                label = "Last.fm",
                url = url,
            )

            else -> Button(
                type = 2,
                style = 5,
                label = platform.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                url = url,
            )
        }
    }

    private fun formatDuration(seconds: Int): String {
        val safe = kotlin.math.max(0, seconds)
        return "~${safe / 60}m${safe % 60}s"
    }

    private fun getWorkshopContentUrl(type: String, contentId: String?): String {
        return contentId?.let { "https://bscm.netlify.app/link/$type/$it" } ?: "https://bscm.netlify.app/"
    }

    private fun buildComponents(trackUrls: List<StreamingRef>): List<ActionRow> {
        if (trackUrls.isEmpty()) return emptyList()

        // Discord constraints
        val maxRows = 5
        val maxButtonsPerRow = 3
        val maxTotalButtons = maxRows * maxButtonsPerRow // 15

        // 1. Deduplicate by platform only (keep first occurrence per platform)
        // This matches the TypeScript logic where processLinksWithPrioritization already deduplicates by platform group
        val seenPlatforms = mutableSetOf<StreamingPlatform>()
        val deduped = trackUrls.filter { link ->
            if (seenPlatforms.contains(link.platform)) {
                false
            } else {
                seenPlatforms.add(link.platform)
                true
            }
        }

        // 2. Prioritize platforms (order list). Unknown or extra remain in tail.
        val priorityOrder = listOf(
            StreamingPlatform.SPOTIFY,
            StreamingPlatform.APPLE_MUSIC,
            StreamingPlatform.YOUTUBE_MUSIC,
            StreamingPlatform.DEEZER,
            StreamingPlatform.TIDAL,
            StreamingPlatform.AMAZON_MUSIC,
            StreamingPlatform.SOUNDCLOUD,
            StreamingPlatform.LAST_FM
        )

        val prioritized = deduped.sortedBy { link ->
            val idx = priorityOrder.indexOf(link.platform)
            if (idx == -1) Int.MAX_VALUE else idx
        }

        // 3. Limit total buttons respecting Discord cap
        val limited = prioritized.take(maxTotalButtons)

        // 4. Build buttons
        val buttons = limited.map { streamingLink -> getButtonForPlatform(streamingLink.platform, streamingLink.url) }

        // 5. Pack into rows up to 3 buttons each, max 5 rows
        val rows = mutableListOf<ActionRow>()
        var idx = 0
        while (idx < buttons.size && rows.size < maxRows) {
            val slice = buttons.subList(idx, kotlin.math.min(idx + maxButtonsPerRow, buttons.size))
            rows.add(ActionRow(type = 1, components = slice))
            idx += maxButtonsPerRow
        }

        // println("Component build debug: input=${trackUrls.size}, deduped=${deduped.size}, finalButtons=${buttons.size}, rows=${rows.size}")

        return rows
    }

    private fun buildWebhookPayload(
        chart: CreateChartRequest,
        author: User,
        attachments: List<BaseAttachment> = emptyList(),
        coverAttachment: Boolean = false,
    ): String {
        val durationFormatted =
            String.format("%dm%ds", (chart.duration / 60).toInt(), (chart.duration % 60).toInt())

        val titleIcons = buildString {
            when (chart.difficulty) {
                Difficulty.HARD -> append(" $hardIcon")
                Difficulty.EXTREME -> append(" $extremeIcon")
                else -> {}
            }
            if (chart.isDeluxe) append(" $deluxeIcon")
            if (chart.isExplicit) append(" $explicitIcon")
        }

        val title = "${chart.track} - ${chart.artist}$titleIcons"

        val fields = listOf(
            EmbedField("Duration", "$durationIcon $durationFormatted", true),
            EmbedField("Notes Amount", "$noteIcon ${chart.notesAmount} notes", true),
            EmbedField("Effects Amount", "$effectIcon ${chart.effectsAmount} effects", false),
        )

        val components = buildComponents(chart.trackUrls)

        val payload = message {
            username(author.username)
            avatar(author.avatarUrl)
            if (coverAttachment) {
                attachment(PresetAttachment(id = "0", filename = "cover.png"))
            }
            attachments(attachments)
            embed {
                this.title = title
                url = "https://bscm.netlify.app/link/chart/${chart.contentId}"
                color = 3820816
                if (coverAttachment) {
                    image("attachment://cover.png")
                } else if (chart.coverUrl.isNotBlank()) {
                    image(chart.coverUrl)
                }
                author("New chart submitted")
                fields.forEach { field(it.name, it.value, it.inline) }
            }
            components.forEach { component(it) }
        }

        return jsonClient.encodeToString(WebhookPayload.serializer(), payload)
    }

    suspend fun uploadChart(
        chart: CreateChartRequest,
        author: User,
        chartBundle: ByteArray,
        coverBytes: ByteArray? = null,
    ): DiscordMessageResponse {
        val hasCover = coverBytes != null
        val payloadJson = buildWebhookPayload(
            chart,
            author,
            coverAttachment = hasCover,
        )
        val normalizedTrack = getNormalizedTrackName(chart.track)

        logger.info("Uploading chart: cover=${chart.coverUrl}, bundle=${chartBundle.size} bytes, coverAttached=$hasCover")

        val response: HttpResponse = applicationHttpClient.submitFormWithBinaryData(
            url = "${webhookUrl}?with_components=true",
            formData = formData {
                append(
                    "payload_json",
                    payloadJson,
                    Headers.build { append(HttpHeaders.ContentType, "application/json") })
                if (hasCover) {
                    append("files[0]", coverBytes, Headers.build {
                        append(
                            HttpHeaders.ContentDisposition,
                            "form-data; name=\"files[0]\"; filename=\"cover.png\""
                        )
                        append(HttpHeaders.ContentType, "image/png")
                    })
                    append("files[1]", chartBundle, Headers.build {
                        append(
                            HttpHeaders.ContentDisposition,
                            "form-data; name=\"files[1]\"; filename=\"${normalizedTrack}_v1.zip\""
                        )
                        append(HttpHeaders.ContentType, ContentType.Application.Zip.toString())
                    })
                } else {
                    append("file0", chartBundle, Headers.build {
                        append(
                            HttpHeaders.ContentDisposition,
                            "form-data; name=\"file0\"; filename=\"${normalizedTrack}_v1.zip\""
                        )
                        append(HttpHeaders.ContentType, ContentType.Application.Zip.toString())
                    })
                }
            }
        )

        if (!response.status.isSuccess()) {
            throw Exception("Failed to send: ${response.status}, ${response.bodyAsText()}")
        }

        val discordResponse = jsonClient.decodeFromString<DiscordMessageResponse>(response.bodyAsText())

        return discordResponse
    }

    suspend fun uploadTourPass(data: TourPassPublishData): DiscordMessageResponse {
        val payloadJson = jsonClient.encodeToString(
            WebhookPayload.serializer(),
            message {
                username(workshopUsername)
                avatar(workshopAvatarUrl)
                embed {
                    title = data.title
                    description = data.description
                    url = getWorkshopContentUrl("tourpass", data.context.contentId)
                    color = 3820816
                    timestamp()
                    author("New tour pass submitted")
                    footer("Submitted by @${data.context.submittedBy.username}", data.context.submittedBy.avatarUrl)
                    data.coverUrl?.takeIf { it.isNotBlank() }?.let { image(it) }
                    thumbnail("")
                    field("Duration", "$durationIcon ${formatDuration(data.durationSeconds)}", true)
                    field("Tracks", "$noteIcon ${data.tracksAmount} songs", true)
                    field(" ", " ", false)
                    data.difficultyLabel?.takeIf { it.isNotBlank() }?.let {
                        field("Difficulty", it, true)
                    }
                    data.trailerUrl?.takeIf { it.isNotBlank() }?.let {
                        field("Trailer", it, true)
                    }
                    if (data.tracklist.isNotEmpty()) {
                        field("Tracklist", data.tracklist.joinToString("\n"), false)
                    }
                }
                buildComponents(data.context.trackUrls).forEach { component(it) }
            }
        )

        val response: HttpResponse = applicationHttpClient.submitFormWithBinaryData(
            url = "${webhookUrl}?with_components=true",
            formData = formData {
                append("payload_json", payloadJson, Headers.build {
                    append(HttpHeaders.ContentType, "application/json")
                })
            }
        )

        val bodyText = response.bodyAsText()

        if (!response.status.isSuccess()) {
            throw Exception("Failed to upload tour pass: ${response.status}, $bodyText")
        }

        if (bodyText.isBlank()) {
            throw Exception("Discord returned empty response body (status ${response.status})")
        }

        return jsonClient.decodeFromString<DiscordMessageResponse>(bodyText)
    }

    suspend fun uploadTheme(data: ThemePublishData): DiscordMessageResponse {
        val payloadJson = jsonClient.encodeToString(
            WebhookPayload.serializer(),
            message {
                username(workshopUsername)
                avatar(workshopAvatarUrl)
                embed {
                    title = data.title
                    description = data.description
                    url = getWorkshopContentUrl("theme", data.context.contentId)
                    color = 3820816
                    timestamp()
                    author("New theme submitted")
                    footer("Submitted by @${data.context.submittedBy.username}", data.context.submittedBy.avatarUrl)
                    data.displayArtUrl?.takeIf { it.isNotBlank() }?.let { image(it) }
                    data.coverArtUrl?.takeIf { it.isNotBlank() }?.let { thumbnail(it) }
                    field("<:refresh:1490158323197022449> Replaces", data.replaces, false)
                    data.trailerUrl?.takeIf { it.isNotBlank() }?.let { field("Trailer", it, false) }
                }
                buildComponents(data.context.trackUrls).forEach { component(it) }
            }
        )

        val response: HttpResponse = applicationHttpClient.submitFormWithBinaryData(
            url = "${webhookUrl}?with_components=true",
            formData = formData {
                append("payload_json", payloadJson, Headers.build {
                    append(HttpHeaders.ContentType, "application/json")
                })
            }
        )

        if (!response.status.isSuccess()) {
            throw Exception("Failed to upload theme: ${response.status}, ${response.bodyAsText()}")
        }

        return jsonClient.decodeFromString<DiscordMessageResponse>(response.bodyAsText())
    }

    @OptIn(InternalAPI::class)
    suspend fun uploadVersion(
        chart: Chart,
        version: CreateVersionRequest,
        author: User,
        chartBundle: ByteArray,
        existingVersions: List<Version>,
    ): DiscordMessageResponse {

        val normalizedTrack = getNormalizedTrackName(chart.track.title)

        val nextIndex = (existingVersions.maxOfOrNull { it.versionCode } ?: 0) + 1

        // We need to update the displayed info with the new version data
        val payloadJson = buildWebhookPayload(
                CreateChartRequest(
                    track = version.track,
                    artist = version.artist,
                    duration = version.duration,
                    notesAmount = version.notesAmount,
                    effectsAmount = version.effectsAmount,
                    difficulty = version.difficulty,
                    isDeluxe = version.isDeluxe,
                    isExplicit = version.isExplicit,
                    trackPreviewUrl = chart.track.previewUrl,
                    bpm = version.bpm,
                    bundleUrl = version.bundleUrl,
                    previewUrl = version.previewUrl,
                    coverUrl = chart.track.coverUrl ?: "",
                    trackUrls = chart.track.streamingRefs,
                    contentId = chart.id,
                    fileSizeBytes = version.fileSizeBytes,
                ),
            author,
                attachments = existingVersions.map {
                SimpleAttachment(
                    id = it.id,
                        filename = "${normalizedTrack}_v${it.versionCode}.zip",
                )
                })

        // println("Current message attachments: $payloadJson")

        val response: HttpResponse = applicationHttpClient.submitFormWithBinaryData(
            url = "${editWebhookUrl}/${chart.discordMessageId}?with_components=true",
            formData = formData {
                append("payload_json", payloadJson, Headers.build {
                    append(HttpHeaders.ContentType, "application/json")
                })
                append("file", chartBundle, Headers.build {
                    append(
                        HttpHeaders.ContentDisposition,
                        "form-data; name=\"file\"; filename=\"${normalizedTrack}_v${nextIndex}.zip\""
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

        logger.info(response.bodyAsText())

        return jsonClient.decodeFromString(DiscordMessageResponse.serializer(), response.bodyAsText())
    }

    suspend fun deleteVersion(
        chart: Chart,
        versions: List<Version>,
        versionId: String
    ): Boolean {
        val messageId = chart.discordMessageId
            ?: throw IllegalStateException("Chart ${chart.id} has no Discord message ID")
        val remainingVersions = versions.filter { it.id != versionId }
        val normalizedTrack = getNormalizedTrackName(chart.track.title)

        val payloadJson = jsonClient.encodeToString(
            SimpleWebhookPayload.serializer(), SimpleWebhookPayload(
                attachments = remainingVersions.map {
                    SimpleAttachment(
                        id = it.id,
                        filename = "${normalizedTrack}_v${it.versionCode}.zip",
                    )
                },
            )
        )

        logger.info("Current message attachments after deletion: $payloadJson")

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

    @Serializable
    data class RefreshData(
        val versionId: String,
        val bundleUrl: String,
        val coverUrl: String? = null,
        val audioUrl: String? = null
    )

    /**
     * Upload an audio file to Discord and return the attachment URL
     * @param audioBytes The audio file bytes (e.g., MP3)
     * @param filename The filename for the attachment
     * @param trackName Track name for the message content
     * @param artistName Artist name for the message content
     * @param originalUrl Original preview URL from the source
     * @return The Discord attachment URL
     */
    suspend fun uploadAudioFile(
        audioBytes: ByteArray,
        filename: String,
        trackName: String,
        artistName: String,
        originalUrl: String
    ): String {
        val payload = jsonClient.encodeToString(
            WebhookPayload.serializer(),
            WebhookPayload(
                content = "**$trackName - $artistName**\nOriginal: $originalUrl",
                attachments = emptyList()
            )
        )

        val response: HttpResponse = applicationHttpClient.submitFormWithBinaryData(
            url = webhookUrl,
            formData = formData {
                append("payload_json", payload, Headers.build {
                    append(HttpHeaders.ContentType, "application/json")
                })
                append("file", audioBytes, Headers.build {
                    append(HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"$filename\"")
                    append(HttpHeaders.ContentType, "audio/mpeg")
                })
            }
        )

        if (!response.status.isSuccess()) {
            throw Exception("Failed to upload audio: ${response.status}, ${response.bodyAsText()}")
        }

        val discordResponse = jsonClient.decodeFromString<DiscordMessageResponse>(response.bodyAsText())
        val audioAttachment = discordResponse.attachments.firstOrNull()
            ?: throw IllegalStateException("Discord response missing audio attachment")

        return audioAttachment.url
    }
}