package org.bscm.utils

import io.github.deficuet.unitykt.ImportContext
import io.github.deficuet.unitykt.UnityAssetManager
import io.github.deficuet.unitykt.classes.TextAsset
import io.github.deficuet.unitykt.classes.Texture2D
import io.github.deficuet.unitykt.firstObjectOf
import io.klogging.noCoLogger
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.bscm.protobuf.Chart
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

object DecodingUtils {
    private val logger = noCoLogger(DecodingUtils::class)

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
            logger.warn(e, "Failed to parse info.json")
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
            logger.warn(e, "Failed to extract Texture2D cover from bundle")
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