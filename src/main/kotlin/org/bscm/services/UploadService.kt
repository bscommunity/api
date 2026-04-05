package org.bscm.services

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bscm.clients.applicationHttpClient
import org.bscm.clients.jsonClient
import org.bscm.interactions.*
import org.bscm.models.Chart
import org.bscm.models.StreamingLink
import org.bscm.models.User
import org.bscm.models.Version
import org.bscm.models.dto.chart.CreateChartRequest
import org.bscm.models.dto.version.CreateVersionRequest
import org.bscm.models.enums.Difficulty
import org.bscm.models.enums.StreamingPlatform
import java.util.*

private val logger = KtorSimpleLogger("UploadService")

class UploadService(
    private val webhookId: String,
    private val webhookToken: String,
    private val botToken: String,
    private val channelId: String,
) {
    data class UploadImage(
        val bytes: ByteArray,
        val filename: String,
        val contentType: ContentType,
    )

    data class TourPassPublishData(
        val title: String,
        val description: String?,
        val contentId: String? = null,
        val uploader: User,
        val coverUrl: String?,
        val coverImage: UploadImage?,
        val durationSeconds: Int,
        val tracksAmount: Int,
        val difficultyLabel: String? = null,
        val trailerUrl: String? = null,
        val tracklist: List<String> = emptyList(),
        val trackUrls: List<StreamingLink> = emptyList(),
    )

    data class ThemePublishData(
        val title: String,
        val description: String?,
        val contentId: String? = null,
        val uploader: User,
        val replaces: String,
        val trailerUrl: String?,
        val coverArtUrl: String?,
        val coverArt: UploadImage?,
        val displayArtUrl: String?,
        val displayArt: UploadImage?,
        val trackUrls: List<StreamingLink> = emptyList(),
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

            else -> Button(
                type = 2,
                style = 5,
                label = platform.name,
                url = url,
            )
        }
    }

    private fun formatDuration(seconds: Int): String {
        val safe = kotlin.math.max(0, seconds)
        return "~${safe / 60}m${safe % 60}s"
    }

    private fun buildComponents(trackUrls: List<StreamingLink>): List<ActionRow> {
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

    // Extended to allow embedding of an attached cover image via Discord's attachment:// scheme
    private fun buildWebhookPayload(
        chart: CreateChartRequest,
        author: User,
        attachments: List<BaseAttachment> = emptyList(),
        embedCoverAsAttachment: Boolean = false,
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

        // println("Streaming links: ${chart.trackUrls}")
        // println("Components: $components")

        val payload = message {
            username(author.username)
            avatar(author.avatarUrl)
            attachments(attachments)
            embed {
                this.title = title
                url = "https://bscm.netlify.app/link/chart/${chart.contentId}"
                color = 3820816
                // If we are attaching the cover image file, reference it using attachment://cover.png
                if (embedCoverAsAttachment) image("attachment://cover.png") else image(chart.coverUrl)
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
        coverImage: ByteArray? = null,
    ): DiscordMessageResponse {
        // Build payload referencing cover image attachment if provided
        val payloadJson = buildWebhookPayload(
            chart,
            author,
            embedCoverAsAttachment = coverImage != null
        )
        val normalizedTrack = getNormalizedTrackName(chart.track)

        logger.info("Uploading chart: cover=${coverImage != null} (${coverImage?.size ?: 0} bytes), bundle=${chartBundle.size} bytes")

        val response: HttpResponse = applicationHttpClient.submitFormWithBinaryData(
            url = "${webhookUrl}?with_components=true",
            formData = formData {
                append(
                    "payload_json",
                    payloadJson,
                    Headers.build { append(HttpHeaders.ContentType, "application/json") })
                // Attach cover image first so we can reliably identify it later
                // Use unique field names (file0, file1) to avoid conflicts
                coverImage?.let { bytes ->
                    append("file0", bytes, Headers.build {
                        append(HttpHeaders.ContentDisposition, "form-data; name=\"file0\"; filename=\"cover.png\"")
                        append(HttpHeaders.ContentType, ContentType.Image.PNG.toString())
                    })
                }
                append("file1", chartBundle, Headers.build {
                    append(
                        HttpHeaders.ContentDisposition,
                        "form-data; name=\"file1\"; filename=\"${normalizedTrack}_v1.zip\""
                    )
                    append(HttpHeaders.ContentType, ContentType.Application.Zip.toString())
                })
            }
        )

        if (!response.status.isSuccess()) {
            throw Exception("Failed to send: ${response.status}, ${response.bodyAsText()}")
        }

        val discordResponse = jsonClient.decodeFromString<DiscordMessageResponse>(response.bodyAsText())

        // println("Uploaded message: ${response.bodyAsText()} and $discordResponse")

        /*println("Discord response: ${discordResponse.attachments.size} attachment(s) received")
        discordResponse.attachments.forEachIndexed { idx, att ->
            println("  [$idx] ${att.filename} (id=${att.id})")
        }*/

        return discordResponse
    }

    suspend fun uploadTourPass(data: TourPassPublishData): DiscordMessageResponse {
        val payloadJson = jsonClient.encodeToString(
            WebhookPayload.serializer(),
            message {
                username("bscm")
                avatar("https://i.imgur.com/7e4lzGf.png")
                embed {
                    title = data.title
                    description = data.description
                    url = data.contentId?.let { "https://bscm.netlify.app/link/tourpass/$it" } ?: "https://bscm.netlify.app/"
                    color = 3820816
                    timestamp()
                    author("New tour pass submitted")
                    footer("Submitted by @${data.uploader.username}", data.uploader.avatarUrl)
                    image(if (data.coverImage != null) "attachment://tourpass-cover.png" else data.coverUrl)
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
                buildComponents(data.trackUrls).forEach { component(it) }
            }
        )

        val response: HttpResponse = applicationHttpClient.submitFormWithBinaryData(
            url = "${webhookUrl}?with_components=true",
            formData = formData {
                append("payload_json", payloadJson, Headers.build {
                    append(HttpHeaders.ContentType, "application/json")
                })
                data.coverImage?.let { image ->
                    append("file", image.bytes, Headers.build {
                        append(HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"tourpass-cover.png\"")
                        append(HttpHeaders.ContentType, image.contentType.toString())
                    })
                }
            }
        )

        if (!response.status.isSuccess()) {
            throw Exception("Failed to upload tour pass: ${response.status}, ${response.bodyAsText()}")
        }

        return jsonClient.decodeFromString<DiscordMessageResponse>(response.bodyAsText())
    }

    suspend fun uploadTheme(data: ThemePublishData): DiscordMessageResponse {
        val payloadJson = jsonClient.encodeToString(
            WebhookPayload.serializer(),
            message {
                username("bscm")
                avatar("https://i.imgur.com/7e4lzGf.png")
                embed {
                    title = data.title
                    description = data.description
                    url = data.contentId?.let { "https://bscm.netlify.app/link/theme/$it" } ?: "https://bscm.netlify.app/"
                    color = 3820816
                    timestamp()
                    author("New theme submitted")
                    footer("Submitted by @${data.uploader.username}", data.uploader.avatarUrl)
                    image(if (data.displayArt != null) "attachment://theme-display-art.png" else data.displayArtUrl)
                    thumbnail(if (data.coverArt != null) "attachment://theme-cover-art.png" else data.coverArtUrl)
                    field("<:refresh:1490158323197022449> Replaces", data.replaces, false)
                    data.trailerUrl?.takeIf { it.isNotBlank() }?.let { field("Trailer", it, false) }
                }
                buildComponents(data.trackUrls).forEach { component(it) }
            }
        )

        val response: HttpResponse = applicationHttpClient.submitFormWithBinaryData(
            url = "${webhookUrl}?with_components=true",
            formData = formData {
                append("payload_json", payloadJson, Headers.build {
                    append(HttpHeaders.ContentType, "application/json")
                })
                data.displayArt?.let { image ->
                    append("file0", image.bytes, Headers.build {
                        append(HttpHeaders.ContentDisposition, "form-data; name=\"file0\"; filename=\"theme-display-art.png\"")
                        append(HttpHeaders.ContentType, image.contentType.toString())
                    })
                }
                data.coverArt?.let { image ->
                    append("file1", image.bytes, Headers.build {
                        append(HttpHeaders.ContentDisposition, "form-data; name=\"file1\"; filename=\"theme-cover-art.png\"")
                        append(HttpHeaders.ContentType, image.contentType.toString())
                    })
                }
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
    ): DiscordMessageResponse {

        val normalizedTrack = getNormalizedTrackName(chart.track)

        // Calculate the next version index (current versions count + 1)
        val nextIndex = chart.versions.size + 1

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
                trackPreviewUrl = chart.trackPreviewUrl,
                bpm = version.bpm,
                bundleUrl = version.bundleUrl,
                previewUrl = version.previewUrl,
                coverUrl = chart.coverUrl,
                trackUrls = chart.trackUrls,
                contentId = chart.contentId,
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
            url = "${editWebhookUrl}/${chart.id}?with_components=true",
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
        messageId: String,
        track: String,
        versions: List<Version>,
        versionId: String
    ): Boolean {
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
     * Upload a generic cover image to Discord and return the resulting CDN URL.
     */
    suspend fun uploadCoverImage(
        coverBytes: ByteArray,
        filename: String = "cover.png",
        contentType: ContentType = ContentType.Image.PNG,
        context: String,
    ): String {
        val payload = jsonClient.encodeToString(
            WebhookPayload.serializer(),
            WebhookPayload(
                content = "Cover upload: $context",
                attachments = emptyList()
            )
        )

        val response: HttpResponse = applicationHttpClient.submitFormWithBinaryData(
            url = webhookUrl,
            formData = formData {
                append("payload_json", payload, Headers.build {
                    append(HttpHeaders.ContentType, "application/json")
                })
                append("file", coverBytes, Headers.build {
                    append(HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"$filename\"")
                    append(HttpHeaders.ContentType, contentType.toString())
                })
            }
        )

        if (!response.status.isSuccess()) {
            throw Exception("Failed to upload cover image: ${response.status}, ${response.bodyAsText()}")
        }

        val discordResponse = jsonClient.decodeFromString<DiscordMessageResponse>(response.bodyAsText())
        return discordResponse.attachments.firstOrNull()?.url
            ?: throw IllegalStateException("Discord response missing cover image attachment")
    }

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