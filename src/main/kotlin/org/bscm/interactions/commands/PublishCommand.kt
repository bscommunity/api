package org.bscm.interactions.commands

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.utils.io.*
import kotlinx.serialization.json.*
import org.bscm.interactions.CommandHandler.ephemeralMessage
import org.bscm.interactions.I18n
import org.bscm.models.repository.IUserRepository
import org.bscm.plugins.applicationHttpClient
import org.bscm.services.ChartPublishService
import org.koin.ktor.ext.getKoin

object PublishCommand {
    suspend fun ApplicationCall.respondJson(json: JsonObject) = respond(json)

    // Updated to receive entire payload so we can access user/member info for discordId lookup
    suspend fun handle(call: ApplicationCall, payload: JsonObject, data: JsonObject, locale: String?) {
        val koin = call.application.getKoin()
        val userRepository = koin.get<IUserRepository>()

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
                    color = 15548997 // Discord red
                }
                // Provide a button to registration page
                buttonRow(
                    org.bscm.interactions.Button(
                        type = 2,
                        style = 5,
                        label = "Create Account",
                        url = "https://bscm.netlify.app"
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
        println(" - Explicit: $explicitVal")

        // EphemeralMessage with embed structured message
        val progressJson = ephemeralMessage {
            embed {
                title = "⏳ ${I18n.t(locale, "publish_received")}"
                description = I18n.t(locale, "processing_chart")
                color = 16776960 // Yellow
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
            println("Failed to download bundle: ${e.message}")
            call.respondJson(ephemeralMessage {
                embed {
                    title = "❌ Error"
                    description = I18n.t(locale, "bundle_download_failed")
                    color = 15548997 // Discord red
                }
            })
            return
        }

        val publishService = call.application.getKoin().get<ChartPublishService>()

        val result = try {
            publishService.publish(
                user = user,
                bundleBytes = bundleBytes,
                overrides = ChartPublishService.Overrides(
                    isExplicit = explicitVal,
                    previewUrl = gameplayUrl,
                )
            )
        } catch (e: Exception) {
            println("Failed to publish chart: ${e.message}")
            e.printStackTrace()
            call.respondJson(ephemeralMessage {
                embed {
                    title = "❌ Error"
                    description = I18n.t(locale, "chart_persist_failed")
                    color = 15548997 // Discord red
                }
            })
            return
        }

        val v = result.initialVersion

        // Success ephemeral message
        val jsonSuccess = ephemeralMessage {
            embed {
                title = "✅ ${I18n.t(locale, "publish_success_title")}"
                description = I18n.t(locale, "publish_success_description")
                field(I18n.t(locale, "track_label"), result.chart.track, true)
                field(I18n.t(locale, "artist_label"), result.chart.artist, true)
                field(I18n.t(locale, "difficulty_label"), v.difficulty.name, true)
                field(I18n.t(locale, "duration_label"), String.format("%dm%ds", (v.duration / 60).toInt(), (v.duration % 60).toInt()), true)
                field(I18n.t(locale, "notes_label"), v.notesAmount.toString(), true)
                field(I18n.t(locale, "effects_label"), v.effectsAmount.toString(), true)
                field("", I18n.t(locale, "manage_chart_info"), false)
                color = 5763719 // Discord green
            }
            buttonRow(
                org.bscm.interactions.Button(
                    type = 2,
                    style = 5,
                    label = "View Chart",
                    url = "https://bscm.netlify.app/link/chart/${result.chart.contentId}"
                ),
                org.bscm.interactions.Button(
                    type = 2,
                    style = 5,
                    label = "Open Dashboard",
                    url = "https://bscm.netlify.app"
                )
            )
        }

        call.respondJson(jsonSuccess)
    }
}