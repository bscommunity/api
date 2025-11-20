package org.bscm.services

import io.github.deficuet.unitykt.ImportContext
import io.github.deficuet.unitykt.UnityAssetManager
import io.github.deficuet.unitykt.classes.TextAsset
import io.github.deficuet.unitykt.classes.Texture2D
import io.github.deficuet.unitykt.firstObjectOf
import org.apache.commons.compress.archivers.zip.ZipFile
import org.bscm.protobuf.Chart // Import parsed chart data class
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class DecodingService {
    companion object {
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
                println("Failed to parse info.json: ${e.message}")
                null
            }
        }

        /**
         * Attempt to extract chart.bytes from a Unity bundle inside the zip.
         */
        fun extractChartFileFromBundle(
            zipBytes: ByteArray,
            targetBundleName: String = "chart.bundle",
        ): ByteArray? {
            val bundleBytes = extractBundleBytesFromZip(zipBytes, targetBundleName) ?: return null
            println("Bundle '$targetBundleName' extracted, size=${bundleBytes.size} bytes.")
            UnityAssetManager.new().use { manager ->
                val context: ImportContext = manager.loadFromByteArray(bundleBytes, targetBundleName)
                val tex: TextAsset = context.objectMap.values.firstObjectOf<TextAsset>()
                println("TextAsset '${tex.mName}' found")
                return tex.mScript
            }
        }

        /**
         * Extracts the cover image from the Unity AssetBundle (Texture2D).
         */
        fun extractCoverImage(zipBytes: ByteArray, bundleName: String = "artwork.bundle"): ByteArray? {
            val bundleBytes = extractBundleBytesFromZip(zipBytes, bundleName) ?: return null
            return try {
                UnityAssetManager.new().use { manager ->
                    val context: ImportContext = manager.loadFromByteArray(bundleBytes, bundleName)
                    // Extract Texture2D asset containing the cover image
                    val tex: Texture2D = context.objectMap.values.firstObjectOf<Texture2D>()
                    // Accessing properties triggers lazy loading
                    val buffered = tex.getImage() ?: return null
                    val out = ByteArrayOutputStream()
                    ImageIO.write(buffered, "png", out) // Always normalize to PNG
                    out.toByteArray()
                }
            } catch (e: Exception) {
                println("Failed to extract Texture2D cover from bundle: ${e.message}")
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
    }
}