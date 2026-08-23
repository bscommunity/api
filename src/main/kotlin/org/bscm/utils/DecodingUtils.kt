package org.bscm.utils

import io.github.deficuet.unitykt.ImportContext
import io.github.deficuet.unitykt.UnityAssetManager
import io.github.deficuet.unitykt.classes.TextAsset
import io.github.deficuet.unitykt.classes.Texture2D
import io.github.deficuet.unitykt.firstObjectOf
import io.ktor.util.logging.*
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.bscm.protobuf.Chart
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

private val logger = KtorSimpleLogger("DecodingUtils")

object DecodingUtils {

    data class BundleInfo(
        val title: String,
        val artist: String,
        val difficulty: Int?,
        val bpm: Int?,
        val type: String?
    )

    /**
     * Reads the uploaded zip bytes and extracts the raw bytes of a specific entry.
     */
    fun extractEntryBytesFromZip(zipBytes: ByteArray, entryNamePredicate: (String) -> Boolean): ByteArray? {
        ZipFile.Builder().setByteArray(zipBytes).get().use { zip ->
            val entries = zip.entries
            for (entry in entries) {
                if (!entry.isDirectory && entryNamePredicate(entry.name)) {
                    zip.getInputStream(entry).use { input ->
                        return input.readBytes()
                    }
                }
            }
        }
        return null
    }

    fun extractBundleBytesFromZip(zipBytes: ByteArray, targetBundleName: String): ByteArray? =
        extractEntryBytesFromZip(zipBytes) { it.equals(targetBundleName, ignoreCase = true) }

    /**
     * Extracts and parses info.json from the root of the zip (client bundle metadata).
     */
    fun extractBundleInfo(zipBytes: ByteArray, infoFileName: String = "info.json"): BundleInfo? {
        val infoBytes = extractEntryBytesFromZip(zipBytes) { it.equals(infoFileName, ignoreCase = true) }
            ?: return null
        return try {
            val json = infoBytes.decodeToString()

            // Very small ad-hoc JSON parsing (avoid bringing full serialization here for just 5 fields)
            fun extract(key: String): String? {
                val regex = Regex("\"$key\"\\s*:\\s*\"(.*?)\"", RegexOption.IGNORE_CASE)
                return regex.find(json)?.groupValues?.getOrNull(1)
            }

            fun extractInt(key: String): Int? {
                val regex = Regex("\"$key\"\\s*:\\s*(\\d+)")
                return regex.find(json)?.groupValues?.getOrNull(1)?.toIntOrNull()
            }
            BundleInfo(
                title = extract("title") ?: extract("track") ?: "",
                artist = extract("artist") ?: "",
                difficulty = extractInt("difficulty"),
                bpm = extractInt("bpm"),
                type = extract("type")
            )
        } catch (e: Exception) {
            logger.error("Failed to parse info.json", e)
            null
        }
    }

    /**
     * Attempt to extract "chart.bytes" from a Unity bundle inside the zip.
     */
    fun extractChartFileFromBundle(
        zipBytes: ByteArray,
        targetBundleName: String = "chart.bundle",
    ): ByteArray? {
        val bundleBytes = extractBundleBytesFromZip(zipBytes, targetBundleName) ?: return null
        // println("Bundle '$targetBundleName' extracted, size=${bundleBytes.size} bytes.")
        UnityAssetManager.Companion.new().use { manager ->
            val context: ImportContext = manager.loadFromByteArray(bundleBytes, targetBundleName)
            val tex: TextAsset = context.objectMap.values.firstObjectOf<TextAsset>()
            // println("TextAsset '${tex.mName}' found")
            return tex.mScript
        }
    }

    /**
     * Extracts the cover image from the Unity AssetBundle (Texture2D).
     */
    fun extractCoverImage(zipBytes: ByteArray, bundleName: String = "artwork.bundle"): ByteArray? {
        val bundleBytes = extractBundleBytesFromZip(zipBytes, bundleName) ?: return null
        return try {
            UnityAssetManager.Companion.new().use { manager ->
                val context: ImportContext = manager.loadFromByteArray(bundleBytes, bundleName)
                // Extract Texture2D asset containing the cover image
                val tex: Texture2D = context.objectMap.values.firstObjectOf<Texture2D>()
                // Accessing properties triggers lazy loading
                val buffered = tex.getImage() ?: return null
                // Fix orientation: Unity Texture2D may be flipped horizontally and rotated 180 degrees
                val rotated = BufferedImage(buffered.width, buffered.height, buffered.type).apply {
                    val g2d = createGraphics()
                    val transform = AffineTransform()
                    transform.scale(-1.0, 1.0)
                    transform.translate(-buffered.width.toDouble(), 0.0)
                    transform.concatenate(
                        AffineTransform.getRotateInstance(
                            Math.PI,
                            buffered.width / 2.0,
                            buffered.height / 2.0
                        )
                    )
                    g2d.transform = transform
                    g2d.drawImage(buffered, 0, 0, null)
                    g2d.dispose()
                }
                val out = ByteArrayOutputStream()
                ImageIO.write(rotated, "png", out) // Always normalize to PNG
                out.toByteArray()
            }
        } catch (e: Exception) {
            logger.error("Failed to extract Texture2D cover from bundle", e)
            null
        }
    }

    /**
     * Compute derived statistics from parsed protobuf chart object.
     */
    fun computeChartStats(parsed: Chart, bpm: Int?): ChartStats {
        val noteOffsets = mutableListOf<Float>()
        parsed.notes.forEach { n ->
            n.single?.note?.let { noteOffsets.add(it.offset) }
            n.long?.notes?.forEach { noteOffsets.add(it.offset) }
            n.switchHold?.positions?.forEach { noteOffsets.add(it.offset) }
        }
        // Effects, speeds, sections might extend total duration
        parsed.effects.forEach { noteOffsets.add(it.offset) }
        parsed.speeds.forEach { noteOffsets.add(it.offset) }
        parsed.sections.forEach { noteOffsets.add(it.offset) }
        val maxOffset = noteOffsets.maxOrNull() ?: 0f
        val bpm = bpm?.toFloat() ?: 120f
        val beatsPerSecond = bpm / 60f
        return ChartStats(
            notesAmount = parsed.notes.size,
            effectsAmount = parsed.effects.sumOf { it.effects.size },
            duration = maxOffset / beatsPerSecond, // Offsets are in beats, convert to seconds
        )
    }

    data class ChartStats(
        val notesAmount: Int,
        val effectsAmount: Int,
        val duration: Float,
    )

    data class BscmContributor(
        val username: String,
        val avatarUrl: String?,
        val role: String,
    )

    interface BscmMetadataBundle {
        fun toJson(): String
    }

    data class BscmMetadata(
        val version: Int = 1,
        val chartId: String,
        val track: String,
        val artist: String,
        val difficulty: Int,
        val isDeluxe: Boolean,
        val isExplicit: Boolean,
        val bpm: Int,
        val duration: Float,
        val notes: Int,
        val effects: Int,
        val contributors: List<BscmContributor>,
        val cover: String?,
    ) : BscmMetadataBundle {
        override fun toJson(): String {
            val sb = StringBuilder()
            sb.appendLine("{")
            sb.appendLine("  \"version\": $version,")
            sb.appendLine("  \"chartId\": \"${chartId.escapeJson()}\",")
            sb.appendLine("  \"track\": \"${track.escapeJson()}\",")
            sb.appendLine("  \"artist\": \"${artist.escapeJson()}\",")
            sb.appendLine("  \"difficulty\": $difficulty,")
            sb.appendLine("  \"isDeluxe\": $isDeluxe,")
            sb.appendLine("  \"isExplicit\": $isExplicit,")
            sb.appendLine("  \"bpm\": $bpm,")
            sb.appendLine("  \"duration\": $duration,")
            sb.appendLine("  \"notes\": $notes,")
            sb.appendLine("  \"effects\": $effects,")
            sb.append("  \"contributors\": [")
            contributors.forEachIndexed { i, c ->
                val comma = if (i < contributors.lastIndex) "," else ""
                sb.appendLine()
                sb.append("    {\"username\": \"${c.username.escapeJson()}\", \"avatarUrl\": ${c.avatarUrl?.let { "\"${it.escapeJson()}\"" } ?: "null"}, \"role\": \"${c.role.escapeJson()}\"}$comma")
            }
            sb.appendLine()
            sb.appendLine("  ],")
            sb.appendLine("  \"cover\": ${cover?.let { "\"${it.escapeJson()}\"" } ?: "null"}")
            sb.append("}")
            return sb.toString()
        }
    }

    data class ThemeBscmMetadata(
        val version: Int = 1,
        val themeId: String,
        val name: String,
        val replaces: String,
        val contributors: List<BscmContributor>,
        val cover: String?,
        val displayArt: String?,
    ) : BscmMetadataBundle {
        override fun toJson(): String {
            val sb = StringBuilder()
            sb.appendLine("{")
            sb.appendLine("  \"version\": $version,")
            sb.appendLine("  \"themeId\": \"${themeId.escapeJson()}\",")
            sb.appendLine("  \"name\": \"${name.escapeJson()}\",")
            sb.appendLine("  \"replaces\": \"${replaces.escapeJson()}\",")
            sb.append("  \"contributors\": [")
            contributors.forEachIndexed { i, c ->
                val comma = if (i < contributors.lastIndex) "," else ""
                sb.appendLine()
                sb.append("    {\"username\": \"${c.username.escapeJson()}\", \"avatarUrl\": ${c.avatarUrl?.let { "\"${it.escapeJson()}\"" } ?: "null"}, \"role\": \"${c.role.escapeJson()}\"}$comma")
            }
            sb.appendLine()
            sb.appendLine("  ],")
            sb.appendLine("  \"cover\": ${cover?.let { "\"${it.escapeJson()}\"" } ?: "null"},")
            sb.appendLine("  \"displayArt\": ${displayArt?.let { "\"${it.escapeJson()}\"" } ?: "null"}")
            sb.append("}")
            return sb.toString()
        }
    }

    private fun String.escapeJson(): String = this
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

    /**
     * Injects a `bscm.json` file into the zip bundle.
     * Returns modified zip bytes with the new entry added.
     */
    fun injectBscmMetadata(zipBytes: ByteArray, metadata: BscmMetadataBundle): ByteArray {
        val bscmJson = metadata.toJson().encodeToByteArray()

        val outputBuffer = ByteArrayOutputStream()
        ZipArchiveOutputStream(outputBuffer).use { zipOut ->
            ZipFile.Builder().setByteArray(zipBytes).get().use { zipIn ->
                // Copy all existing entries
                for (entry in zipIn.entries) {
                    if (entry.isDirectory) continue
                    val entryData = zipIn.getInputStream(entry).use { it.readBytes() }
                    val newEntry = ZipArchiveEntry(entry.name)
                    newEntry.size = entryData.size.toLong()
                    zipOut.putArchiveEntry(newEntry)
                    zipOut.write(entryData)
                    zipOut.closeArchiveEntry()
                }

                // Add bscm.json
                val bscmEntry = ZipArchiveEntry("bscm.json")
                bscmEntry.size = bscmJson.size.toLong()
                zipOut.putArchiveEntry(bscmEntry)
                zipOut.write(bscmJson)
                zipOut.closeArchiveEntry()
            }
        }
        return outputBuffer.toByteArray()
    }

    /**
     * Injects/appends additional data to the info.json file within the zip bundle.
     * Returns modified zip bytes with updated info.json.
     *
     * @param zipBytes The original zip bundle bytes
     * @param infoToAppend Map of key-value pairs to append to info.json
     * @param infoFileName Name of the info file to modify (default: "info.json")
     * @return Modified zip bytes with updated info.json
     */
    fun injectInfoToBundle(
        zipBytes: ByteArray,
        infoToAppend: Map<String, Any>,
        infoFileName: String = "info.json"
    ): ByteArray {
        val outputBuffer = ByteArrayOutputStream()
        ZipArchiveOutputStream(outputBuffer).use { zipOut ->
            ZipFile.Builder().setByteArray(zipBytes).get().use { zipIn ->
                val entries = zipIn.entries.toList()

                for (entry in entries) {
                    if (entry.isDirectory) continue

                    val entryData = if (entry.name.equals(infoFileName, ignoreCase = true)) {
                        // Read existing info.json
                        val existingBytes = zipIn.getInputStream(entry).use { it.readBytes() }
                        val existingJson = existingBytes.decodeToString()

                        // Append new data to info.json
                        val modifiedJson = appendToJson(existingJson, infoToAppend)
                        modifiedJson.encodeToByteArray()
                    } else {
                        // Keep other entries as-is
                        zipIn.getInputStream(entry).use { it.readBytes() }
                    }

                    // Write entry to output zip
                    val newEntry = ZipArchiveEntry(entry.name)
                    newEntry.size = entryData.size.toLong()
                    zipOut.putArchiveEntry(newEntry)
                    zipOut.write(entryData)
                    zipOut.closeArchiveEntry()
                }
            }
        }
        return outputBuffer.toByteArray()
    }

    /**
     * Helper function to append key-value pairs to a JSON string.
     * Handles basic JSON format (assumes simple structure).
     */
    private fun appendToJson(json: String, toAppend: Map<String, Any>): String {
        var result = json.trimEnd()

        // Remove trailing closing brace if present
        if (result.endsWith("}")) {
            result = result.dropLast(1).trimEnd()
            if (!result.endsWith(",")) {
                result += ","
            }
        } else {
            result += ","
        }

        // Add new fields
        val newFields = toAppend.map { (key, value) ->
            val valueStr = when (value) {
                is String -> "\"$value\""
                is Number -> value.toString()
                is Boolean -> value.toString()
                else -> "\"$value\""
            }
            "\"$key\": $valueStr"
        }.joinToString(", ")

        result += "\n  $newFields\n}"
        return result
    }
}