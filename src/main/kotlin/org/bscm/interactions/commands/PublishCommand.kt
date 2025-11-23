package org.bscm.interactions.commands

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.utils.io.*
import kotlinx.serialization.json.*
import org.bscm.interactions.CommandHandler.ephemeralMessage
import org.bscm.interactions.I18n
import org.bscm.models.dto.chart.CreateChartRequest
import org.bscm.models.enums.Difficulty
import org.bscm.models.repository.IChartRepository
import org.bscm.models.repository.IUserRepository
import org.bscm.plugins.applicationHttpClient
import org.bscm.protobuf.ChartParser
import org.bscm.services.DecodingService
import org.bscm.services.MediaInfoService
import org.bscm.services.UploadService
import org.bscm.utils.NanoIdUtils
import org.koin.ktor.ext.getKoin

object PublishCommand {
    suspend fun ApplicationCall.respondJson(json: JsonObject) = respond(json)

    // Updated to receive entire payload so we can access user/member info for discordId lookup
    suspend fun handle(call: ApplicationCall, payload: JsonObject, data: JsonObject, locale: String?) {
        val koin = call.application.getKoin()
        val userRepository = koin.get<IUserRepository>()
        val chartRepository = koin.get<IChartRepository>()
        val uploadService = koin.get<UploadService>()

        // Extract Discord user id (guild -> member.user.id, DM -> user.id)
        val discordUserId = payload["member"]?.jsonObject
            ?.get("user")?.jsonObject
            ?.get("id")?.jsonPrimitive?.contentOrNull
            ?: payload["user"]?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull

        if (discordUserId == null) {
            call.respondJson(ephemeralMessage { content(I18n.t(locale, "missing_user_context")) })
            return
        }

        // Check if bscm account exists for this discord user
        val user = userRepository.getUserByDiscordId(discordUserId)
        if (user == null) {
            // Invite to create account
            val json = ephemeralMessage {
                embed {
                    title = I18n.t(locale, "account_required_title")
                    description = I18n.t(locale, "account_required_description")
                    field(I18n.t(locale, "next_steps_label"), I18n.t(locale, "create_account_steps"), false)
                    footer("bscm")
                }
                // Provide a button to registration page
                buttonRow(
                    org.bscm.interactions.Button(
                        type = 2,
                        style = 5,
                        label = "Open Dashboard",
                        url = "https://bscm.netlify.app/register"
                    )
                )
            }
            call.respondJson(json)
            return
        }

        val optionsArray = data["options"]?.jsonArray
        if (optionsArray == null || optionsArray.isEmpty()) {
            call.respondJson(ephemeralMessage { content(I18n.t(locale, "provide_required_attachments")) })
            return
        }

        val options = optionsArray.mapNotNull { it.jsonObject }
        fun findOption(n: String) = options.firstOrNull { it["name"]?.jsonPrimitive?.content == n }?.jsonObject

        val bundleOpt = findOption("bundle_zip")
        val gameplayOpt = findOption("gameplay_url")
        val explicitOpt = findOption("is_explicit")

        if (bundleOpt == null) {
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

        val explicitVal = explicitOpt?.get("value")?.jsonPrimitive?.booleanOrNull ?: false
        val bundleName = attachmentLabel(bundleOpt)
        val gameplayUrl = gameplayOpt?.get("value")?.jsonPrimitive?.contentOrNull

        println("Publish command received:")
        println(" - Bundle: $bundleName")
        println(" - Gameplay URL: ${gameplayUrl ?: "N/A"}")
        println(" - Explicit: ${explicitVal ?: "N/A"}")

        // EphemeralMessage with embed structured message
        val progressJson = ephemeralMessage {
            embed {
                title = I18n.t(locale, "publish_received")
                description = I18n.t(locale, "processing_not_implemented")
                field(I18n.t(locale, "bundle_label"), bundleName, inline = false)
                gameplayUrl?.let { field(I18n.t(locale, "gameplay_url_label"), it, inline = false) }
                field(I18n.t(locale, "explicit_label"), explicitVal.toString(), inline = false)
                footer("bscm")
            }
        }

        call.respondJson(progressJson)

        // ====== Download bundle from Discord CDN ======
        val bundleId = bundleOpt["value"]?.jsonPrimitive?.contentOrNull
        val bundleAttachmentObj = bundleId?.let { attachments?.get(it)?.jsonObject }
        val bundleUrl = bundleAttachmentObj?.get("url")?.jsonPrimitive?.contentOrNull

        if (bundleUrl == null) {
            call.respondJson(ephemeralMessage { content(I18n.t(locale, "bundle_download_failed")) })
            return
        }

        val bundleBytes: ByteArray = try {
            val resp: HttpResponse = applicationHttpClient.get(bundleUrl)
            resp.bodyAsChannel().toByteArray()
        } catch (e: Exception) {
            call.respondJson(ephemeralMessage { content(I18n.t(locale, "bundle_download_failed")) })
            return
        }

        // 1. Extract info.json metadata
        val bundleInfo = DecodingService.extractBundleInfo(bundleBytes)

        // 2. Extract cover image (raw bytes) if any
        val coverBytes = DecodingService.extractCoverImage(bundleBytes)

        // 3. Extract chart.bytes from chart.bundle and parse protobuf
        val chartBytes = DecodingService.extractChartFileFromBundle(bundleBytes)
        if (chartBytes == null) {
            call.respondJson(ephemeralMessage { content(I18n.t(locale, "chart_bytes_failed")) })
            return
        }
        val parsed = try {
            ChartParser.parse(chartBytes)
        } catch (e: Exception) {
            call.respondJson(ephemeralMessage { content(I18n.t(locale, "chart_parse_failed")) })
            return
        }
        val computedStats = DecodingService.computeChartStats(parsed, bundleInfo?.bpm)

        // 4. Derive difficulty from bundleInfo.difficulty mapping to enum
        val difficultyEnum = when (bundleInfo?.difficulty) {
            4 -> Difficulty.NORMAL
            3 -> Difficulty.HARD
            1 -> Difficulty.EXTREME
            else -> Difficulty.NORMAL
        }

        // 5. Fetch media info (album cover, streaming links) using track + artist from bundleInfo overrides
        val trackName = bundleInfo?.title ?: "Unknown"
        val artistName = bundleInfo?.artist ?: "Unknown"
        val mediaInfo = try { MediaInfoService.getMediaInfo(trackName, artistName) } catch (_: Exception) { null }

        // Fallback cover: if extracted coverBytes available, upload later; else use mediaInfo.coverUrl
        val coverUrlPlaceholder = if (coverBytes != null) "" else (mediaInfo?.coverUrl ?: "")

        // 6. Track URLs (streaming links).
        val streamingLinks = try {
            if (!mediaInfo?.trackUrls.isNullOrEmpty()) {
                MediaInfoService.getTrackStreamingLinks(mediaInfo.trackUrls.first().url, trackName, artistName)
            } else mediaInfo?.trackUrls ?: emptyList()
        } catch (_: Exception) {
            mediaInfo?.trackUrls ?: emptyList()
        }

        // 7. BPM: prefer bundleInfo.bpm else approximate
        val bpm = bundleInfo?.bpm ?: 0

        // 8. isDeluxe flag
        val isDeluxe = bundleInfo?.type?.equals("Promode", ignoreCase = true) ?: false

        // 9. Generate contentId
        val contentId = NanoIdUtils.generateOptimized(
            10,
            "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ",
            63,
            16
        )

        // 10. Upload bundle to Discord (cover image uploading handled by UploadService)
        val createChartForUpload = CreateChartRequest(
            artist = artistName,
            track = trackName,
            album = mediaInfo?.album,
            trackUrls = streamingLinks,
            trackPreviewUrl = mediaInfo?.trackPreviewUrl,
            coverUrl = coverUrlPlaceholder,
            genre = mediaInfo?.genre,
            isExplicit = explicitVal,
            duration = computedStats.duration,
            notesAmount = computedStats.notesAmount,
            effectsAmount = computedStats.effectsAmount,
            bpm = bpm,
            difficulty = difficultyEnum,
            isDeluxe = isDeluxe,
            bundleUrl = "",
            previewUrl = gameplayUrl,
            contentId = contentId,
        )

        val discordResponse = try {
            uploadService.uploadChart(createChartForUpload, user, bundleBytes, coverBytes)
        } catch (e: Exception) {
            call.respondJson(ephemeralMessage { content(I18n.t(locale, "discord_upload_failed")) })
            return
        }

        val bundleAttachment = discordResponse.attachments.firstOrNull { it.filename.endsWith(".zip") }
        val coverUrl = discordResponse.embeds.firstOrNull()?.image?.url

        if (bundleAttachment == null) {
            call.respondJson(ephemeralMessage { content(I18n.t(locale, "discord_upload_missing_bundle")) })
            return
        }

        val finalCreate = createChartForUpload.copy(
            id = discordResponse.id.toULong(),
            versionId = bundleAttachment.id.toULong(),
            bundleUrl = bundleAttachment.url,
            coverUrl = coverUrl ?: createChartForUpload.coverUrl,
        )

        val createdChart = try {
            chartRepository.createChart(user.id, finalCreate)
        } catch (e: Exception) {
            call.respondJson(ephemeralMessage { content(I18n.t(locale, "chart_persist_failed")) })
            return
        }

        // Success ephemeral message
        val successJson = ephemeralMessage {
            embed {
                title = I18n.t(locale, "publish_success_title")
                description = I18n.t(locale, "publish_success_description")
                field("Track", createdChart.track, true)
                field("Artist", createdChart.artist, true)
                field("Difficulty", createChartForUpload.difficulty.name, true)
                field("Duration",
                    String.format("%dm%ds", (createChartForUpload.duration / 60).toInt(), (createChartForUpload.duration % 60).toInt()), true)
                field("Notes", createChartForUpload.notesAmount.toString(), true)
                field("Effects", createChartForUpload.effectsAmount.toString(), true)
                field("Link", "https://bscm.netlify.app/link/chart/${createdChart.contentId}", false)
                footer("bscm")
            }
        }

        call.respondJson(successJson)
    }
}
