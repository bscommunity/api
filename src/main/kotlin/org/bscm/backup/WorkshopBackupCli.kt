package org.bscm.backup

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.bscm.services.RefreshService
import org.bscm.services.UploadService.RefreshData
import org.bscm.services.track.resolvers.applicationHttpClient
import org.bscm.services.track.resolvers.jsonClient
import java.nio.charset.StandardCharsets
import java.nio.file.Path

private val log = KtorSimpleLogger("WorkshopBackupCLI")

fun main(args: Array<String>) = runBlocking {
    val options = parseCliOptions(args) ?: return@runBlocking

    log.info("Starting workshop backup for channel ${options.channelId}")

    val refreshService = RefreshService(
        botToken = options.botToken,
        channelId = options.channelId,
    )

    val refreshData = refreshService.refreshBundleUrls()
    if (refreshData.isEmpty()) {
        log.warn("No workshop messages found in channel ${options.channelId}")
        return@runBlocking
    }

    val generatedAt = Clock.System.now().toString()
    val buildResult = buildBackupPackage(
        channelId = options.channelId,
        generatedAt = generatedAt,
        refreshData = refreshData,
    )

    if (buildResult.entries.isEmpty()) {
        log.warn("No downloadable workshop bundles were found. Nothing was written.")
        return@runBlocking
    }

    val manifest = WorkshopBackupManifest(
        channelId = options.channelId,
        generatedAt = generatedAt,
        totalEntries = buildResult.entries.size,
        entries = buildResult.entries,
    )

    val files = buildList {
        add(buildManifestFile(manifest))
        addAll(buildResult.files)
    }

    WorkshopBackupExporter.write(options.outputPath, WorkshopBackupPackage(files = files))
    log.info("Workshop backup completed with ${buildResult.entries.size} item(s).")
}

private data class CliOptions(
    val botToken: String,
    val channelId: String,
    val outputPath: Path,
)

private data class BuildResult(
    val entries: List<WorkshopBackupEntryInfo>,
    val files: List<WorkshopBackupFile>,
)

private fun parseCliOptions(args: Array<String>): CliOptions? {
    if (args.any { it == "-h" || it == "--help" }) {
        printUsage()
        return null
    }

    val named = mutableMapOf<String, String>()
    val positional = mutableListOf<String>()

    var index = 0
    while (index < args.size) {
        val arg = args[index]
        when {
            arg.startsWith("--") -> {
                val keyValue = arg.removePrefix("--").split("=", limit = 2)
                when (keyValue.size) {
                    2 -> named[keyValue[0].lowercase()] = keyValue[1]
                    1 -> {
                        val key = keyValue[0].lowercase()
                        val next = args.getOrNull(index + 1)
                        if (next == null || next.startsWith("-")) {
                            throw IllegalArgumentException("Missing value for --$key")
                        }
                        named[key] = next
                        index++
                    }
                }
            }
            arg.startsWith("-") -> {
                val key = when (arg.lowercase()) {
                    "-t" -> "token"
                    "-c" -> "channel"
                    "-o" -> "output"
                    else -> throw IllegalArgumentException("Unknown flag: $arg")
                }
                val next = args.getOrNull(index + 1)
                    ?: throw IllegalArgumentException("Missing value for $arg")
                named[key] = next
                index++
            }
            else -> positional += arg
        }
        index++
    }

    val botToken = firstPresent(named, positional, listOf("token", "bot-token", "bot_token"), 0)
        ?: System.getenv("DISCORD_BOT_TOKEN")
        ?: System.getenv("BOT_TOKEN")
        ?: return missingOption("bot token")

    val channelId = firstPresent(named, positional, listOf("channel", "channel-id", "channel_id"), 1)
        ?: System.getenv("DISCORD_CHANNEL_ID")
        ?: System.getenv("CHANNEL_ID")
        ?: return missingOption("channel id")

    val output = firstPresent(named, positional, listOf("output", "out", "path"), 2)
        ?: return missingOption("output path")

    return CliOptions(
        botToken = botToken,
        channelId = channelId,
        outputPath = Path.of(output),
    )
}

private fun firstPresent(
    named: Map<String, String>,
    positional: List<String>,
    keys: List<String>,
    positionalIndex: Int,
): String? {
    keys.forEach { key ->
        named[key]?.let { return it }
    }
    return positional.getOrNull(positionalIndex)
}

private fun missingOption(name: String): CliOptions? {
    log.error("Missing required $name")
    printUsage()
    return null
}

private fun printUsage() {
    log.info(
        "Usage: <botToken> <channelId> <outputPath> | " +
            "--token <botToken> --channel <channelId> --output <outputPath>"
    )
    log.info("If outputPath ends with .zip, the backup is written as a single archive; otherwise it is written as a folder.")
}

private suspend fun buildBackupPackage(
    channelId: String,
    generatedAt: String,
    refreshData: Map<String, RefreshData>,
): BuildResult {
    val entries = mutableListOf<WorkshopBackupEntryInfo>()
    val files = mutableListOf<WorkshopBackupFile>()

    refreshData.forEach { (messageId, data) ->
        val downloadedAt = Clock.System.now().toString()
        val basePath = "messages/$messageId"
        val bundlePath = "$basePath/bundle.zip"
        val infoPath = "$basePath/info.json"
        val coverPath = data.coverUrl?.let { "$basePath/cover.png" }

        val bundleBytes = try {
            downloadBytes(data.bundleUrl)
        } catch (e: Exception) {
            log.warn("Skipping message $messageId because the bundle could not be downloaded: ${e.message}")
            return@forEach
        }

        val coverBytes = data.coverUrl?.let { coverUrl ->
            try {
                downloadBytes(coverUrl)
            } catch (e: Exception) {
                log.warn("Message $messageId: cover download failed, continuing without it (${e.message})")
                null
            }
        }

        val entry = WorkshopBackupEntryInfo(
            messageId = messageId,
            versionId = data.versionId,
            bundleUrl = data.bundleUrl,
            coverUrl = data.coverUrl,
            audioUrl = data.audioUrl,
            bundlePath = bundlePath,
            coverPath = coverPath,
            bundleSizeBytes = bundleBytes.size.toLong(),
            coverSizeBytes = coverBytes?.size?.toLong(),
            downloadedAt = downloadedAt,
        )

        entries += entry
        files += WorkshopBackupFile(bundlePath, bundleBytes)
        coverBytes?.let { files += WorkshopBackupFile(coverPath!!, it) }
        files += WorkshopBackupFile(
            infoPath,
            jsonClient.encodeToString(WorkshopBackupEntryInfo.serializer(), entry).toByteArray(StandardCharsets.UTF_8),
        )
    }

    if (entries.isNotEmpty()) {
        log.info("Prepared backup package for channel $channelId with ${entries.size} entry(s) generated at $generatedAt")
    }

    return BuildResult(entries = entries, files = files)
}

private suspend fun downloadBytes(url: String): ByteArray {
    val response: HttpResponse = applicationHttpClient.get(url)
    if (!response.status.isSuccess()) {
        throw IllegalStateException("HTTP ${response.status.value} while downloading $url")
    }
    return response.bodyAsChannel().toByteArray()
}



