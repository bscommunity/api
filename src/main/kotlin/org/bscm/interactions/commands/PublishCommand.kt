package org.bscm.interactions.commands


import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import org.bscm.interactions.Button
import org.bscm.interactions.CommandHandler.immediateEphemeralResponse
import org.bscm.interactions.I18n
import org.bscm.interactions.message
import org.bscm.models.interfaces.IUserRepository
import org.bscm.services.ChartPublishService
import org.bscm.services.InteractionResponseService
import org.bscm.services.track.resolvers.applicationHttpClient
import org.koin.ktor.ext.getKoin

private val log = KtorSimpleLogger("PublishCommand")

object PublishCommand {
    suspend fun ApplicationCall.respondJson(json: JsonObject) = respond(json)

    suspend fun handle(call: ApplicationCall, payload: JsonObject, data: JsonObject, locale: String?) {
        val koin = call.application.getKoin()
        val userRepository = koin.get<IUserRepository>()
        val interactionService = koin.get<InteractionResponseService>()

        // Extract Discord user id (guild -> member.user.id, DM -> user.id)
        val discordUserId = payload["member"]?.jsonObject
            ?.get("user")?.jsonObject
            ?.get("id")?.jsonPrimitive?.contentOrNull
            ?: payload["user"]?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull

        if (discordUserId == null) {
            call.respondJson(immediateEphemeralResponse { content(I18n.t(locale, "missing_user_context")) })
            return
        }

        // Check if bscm account exists for this discord user
        val user = userRepository.getUserByDiscordId(discordUserId)
        if (user == null) {
            // Invite to create account
            val json = immediateEphemeralResponse {
                embed {
                    title = "⚠️ ${I18n.t(locale, "account_required_title")}"
                    description = I18n.t(locale, "account_required_description")
                    field(I18n.t(locale, "next_steps_label"), I18n.t(locale, "create_account_steps"), false)
                    color = 16776960 // Yellow
                }
                // Provide a button to registration page
                buttonRow(
                    Button(
                        type = 2,
                        style = 5,
                        label = I18n.t(locale, "account_create_button"),
                        url = "https://bscm.netlify.app"
                    )
                )
            }
            call.respondJson(json)
            return
        }

        val optionsArray = data["options"]?.jsonArray
        if (optionsArray == null || optionsArray.isEmpty()) {
            call.respondJson(immediateEphemeralResponse { content(I18n.t(locale, "provide_required_attachments")) })
            return
        }

        val options = optionsArray.mapNotNull { it.jsonObject }
        fun findOption(n: String) = options.firstOrNull { it["name"]?.jsonPrimitive?.content == n }?.jsonObject

        val bundleOpt = findOption("bundle_zip")
        val gameplayOpt = findOption("gameplay_url")
        // val explicitOpt = findOption("is_explicit")

        if (bundleOpt == null) {
            call.respondJson(immediateEphemeralResponse { content(I18n.t(locale, "missing_required_attachments")) })
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

        val explicitVal = false // explicitOpt?.get("value")?.jsonPrimitive?.booleanOrNull ?: false
        val bundleName = attachmentLabel(bundleOpt)
        val gameplayUrl = gameplayOpt?.get("value")?.jsonPrimitive?.contentOrNull

        // ====== Step 1: Send deferred response (acknowledges interaction immediately) ======
        call.respondJson(interactionService.deferredResponse(ephemeral = true))

        // Extract interaction token for follow-ups
        val interactionToken = payload["token"]?.jsonPrimitive?.contentOrNull
        if (interactionToken == null) {
            log.error("Missing interaction token")
            return
        }

        // ====== Step 2: Process in background and update via editOriginalResponse ======
        call.application.launch {
            try {
                // Update: Downloading bundle
                interactionService.editOriginalResponse(interactionToken, message {
                    embed {
                        title = "📥 ${I18n.t(locale, "downloading_bundle")}"
                        description = I18n.t(locale, "downloading_bundle_description")
                        color = 3447003 // Blue
                    }
                })

                val bundleId = bundleOpt["value"]?.jsonPrimitive?.contentOrNull
                val bundleAttachmentObj = bundleId?.let { attachments?.get(it)?.jsonObject }
                val bundleUrl = bundleAttachmentObj?.get("url")?.jsonPrimitive?.contentOrNull

                if (bundleUrl == null) {
                    interactionService.editOriginalResponse(interactionToken, message {
                        embed {
                            title = "❌ ${I18n.t(locale, "error")}"
                            description = I18n.t(locale, "bundle_download_failed")
                            color = 15548997 // Discord red
                        }
                    })
                    return@launch
                }

                val bundleBytes: ByteArray = try {
                    val resp: HttpResponse = applicationHttpClient.get(bundleUrl)
                    resp.bodyAsChannel().toByteArray()
                } catch (e: Exception) {
                    log.error("Failed to download bundle: ${e.message}")
                    interactionService.editOriginalResponse(interactionToken, message {
                        embed {
                            title = "❌ ${I18n.t(locale, "error")}"
                            description = I18n.t(locale, "bundle_download_failed")
                            color = 15548997 // Discord red
                        }
                    })
                    return@launch
                }

                // Update: Processing chart
                interactionService.editOriginalResponse(interactionToken, message {
                    embed {
                        title = "⚙️ ${I18n.t(locale, "processing_chart")}"
                        description = I18n.t(locale, "processing_chart_description")
                        color = 16776960 // Yellow
                    }
                })

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
                    log.error("Failed to publish chart: ${e.message}")
                    e.printStackTrace()
                    interactionService.editOriginalResponse(interactionToken, message {
                        embed {
                            title = "❌ ${I18n.t(locale, "error")}"
                            description = I18n.t(locale, "chart_persist_failed") + "\n\n```${e.message}```"
                            color = 15548997 // Discord red
                        }
                    })
                    return@launch
                }

                val v = result.initialVersion

                // Update: Success!
                interactionService.editOriginalResponse(interactionToken, message {
                    embed {
                        title = "✅ ${I18n.t(locale, "publish_success_title")}"
                        description = I18n.t(locale, "publish_success_description")
                        field(I18n.t(locale, "track_label"), result.chart.track.title, true)
                        field(I18n.t(locale, "artist_label"), result.chart.track.artist, true)
                        field(I18n.t(locale, "difficulty_label"), v.difficulty.name, true)
                        field(I18n.t(locale, "duration_label"), String.format("%dm%ds", (v.duration / 60).toInt(), (v.duration % 60).toInt()), true)
                        field(I18n.t(locale, "notes_label"), v.notesAmount.toString(), true)
                        field(I18n.t(locale, "effects_label"), v.effectsAmount.toString(), true)
                        footer(I18n.t(locale, "manage_chart_info"))
                        color = 5763719 // Discord green
                    }
                    buttonRow(
                        Button(
                            type = 2,
                            style = 5,
                            label = I18n.t(locale, "view_chart_button"),
                            url = "https://bscm.netlify.app/link/chart/${result.chart.id}"
                        ),
                        Button(
                            type = 2,
                            style = 5,
                            label = I18n.t(locale, "open_dashboard_button"),
                            url = "https://bscm.netlify.app/dashboard/uploads"
                        )
                    )
                })
            } catch (e: Exception) {
                log.error("Unexpected error in publish command: ${e.message}")
                // e.printStackTrace()
                interactionService.editOriginalResponse(interactionToken, message {
                    embed {
                        title = "❌ ${I18n.t(locale, "error")}"
                        description = "${I18n.t(locale, "unexpected_error")}\n\n```${e.message}```"
                        color = 15548997 // Discord red
                    }
                })
            }
        }
    }
}