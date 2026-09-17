package org.bscm.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards the `bscm.json` bundle metadata both publish services inject.
 *
 * The Android app parses this file ([MetadataParser]) and renders its
 * `contributors` array as "The following users contributed to this chart".
 * An author-only array here means mobile credits only the author, no matter
 * what rows exist in the API database — so creation must embed every initial
 * contributor, one entry per user with Android-compatible lowercase role names.
 */
class DecodingUtilsMetadataTest {

    private val contributors = listOf(
        DecodingUtils.MetadataContributor(
            username = "authorUser",
            avatarUserId = "author-id",
            roles = listOf("author"),
        ),
        DecodingUtils.MetadataContributor(
            username = "charterUser",
            avatarUserId = null,
            roles = listOf("chart", "audio"),
        ),
        DecodingUtils.MetadataContributor(
            username = "audioUser",
            avatarUserId = "audio-id",
            roles = listOf("audio"),
        ),
    )

    @Test
    fun `chart metadata embeds every initial contributor`() {
        val json = DecodingUtils.ChartMetadata(
            catalogId = "abc123",
            track = "Track",
            artist = "Artist",
            difficulty = 1,
            isDeluxe = false,
            isExplicit = false,
            bpm = 120,
            duration = 187f,
            notes = 512,
            effects = 64,
            contributors = contributors,
            coverId = "cover-id",
        ).toJson()

        assertTrue(json.contains("\"username\":\"authorUser\""), "author missing:\n$json")
        assertTrue(json.contains("\"username\":\"charterUser\""), "charter missing:\n$json")
        assertTrue(json.contains("\"username\":\"audioUser\""), "audio contributor missing:\n$json")
        assertTrue(json.contains("\"roles\":[\"chart\",\"audio\"]"), "grouped lowercase roles missing:\n$json")
        assertTrue(json.contains("\"avatar\":\"author-id\""), "avatar id missing:\n$json")
        assertTrue(
            json.contains("{\"username\":\"charterUser\",\"roles\":[\"chart\",\"audio\"]}"),
            "avatar must be omitted when null:\n$json",
        )
        assertFalse(json.contains("\n"), "manifest must be minified:\n$json")
    }

    @Test
    fun `theme metadata embeds every initial contributor`() {
        val json = DecodingUtils.ThemeMetadata(
            catalogId = "abc123",
            name = "Theme",
            replaces = "replaces-id",
            contributors = contributors,
        ).toJson()

        assertTrue(json.contains("\"username\":\"authorUser\""), "author missing:\n$json")
        assertTrue(json.contains("\"username\":\"charterUser\""), "charter missing:\n$json")
        assertTrue(json.contains("\"username\":\"audioUser\""), "audio contributor missing:\n$json")
    }

    @Test
    fun `chart metadata embeds audio preview key and video id`() {
        val metadata = DecodingUtils.ChartMetadata(
            catalogId = "abc123",
            track = "Track",
            artist = "Artist",
            difficulty = 1,
            isDeluxe = false,
            isExplicit = false,
            bpm = 120,
            duration = 187f,
            notes = 512,
            effects = 64,
            contributors = contributors,
            coverId = "cover-id",
            audioTrackId = "track-id",
            previewVideoId = "dQw4w9WgXcQ",
        )
        val json = metadata.toJson()

        assertEquals(1, metadata.version, "chart manifest must be v1")
        assertTrue(json.contains("\"version\":1"), "version missing:\n$json")
        assertTrue(json.contains("\"audio\":\"track-id\""), "audio id missing:\n$json")
        assertTrue(json.contains("\"video\":\"dQw4w9WgXcQ\""), "video id missing:\n$json")
    }

    @Test
    fun `chart metadata omits unset previews`() {
        val json = DecodingUtils.ChartMetadata(
            catalogId = "abc123",
            track = "Track",
            artist = "Artist",
            difficulty = 1,
            isDeluxe = false,
            isExplicit = false,
            bpm = 120,
            duration = 187f,
            notes = 512,
            effects = 64,
            contributors = contributors,
            coverId = "cover-id",
        ).toJson()

        assertFalse(json.contains("\"audio\":"), "unset audio must be omitted:\n$json")
        assertFalse(json.contains("\"video\":"), "unset video must be omitted:\n$json")
    }

    @Test
    fun `theme metadata embeds video id`() {
        val json = DecodingUtils.ThemeMetadata(
            catalogId = "abc123",
            name = "Theme",
            replaces = "replaces-id",
            contributors = contributors,
            previewVideoId = "dQw4w9WgXcQ",
        ).toJson()

        assertTrue(json.contains("\"video\":\"dQw4w9WgXcQ\""), "video id missing:\n$json")
    }

    @Test
    fun `chart bundle carries fixed bscm_json manifest`() {
        val metadata = DecodingUtils.ChartMetadata(
            catalogId = "abc123",
            track = "Track",
            artist = "Artist",
            difficulty = 1,
            isDeluxe = false,
            isExplicit = false,
            bpm = 120,
            duration = 187f,
            notes = 512,
            effects = 64,
            contributors = contributors,
            coverId = "cover-id",
        )

        val entries = zipEntryNames(DecodingUtils.injectMetadata(emptyZip(), metadata))

        assertTrue(entries.contains("bscm.json"), "chart manifest entry missing: $entries")
    }

    @Test
    fun `theme bundle carries per-id manifest`() {
        val metadata = DecodingUtils.ThemeMetadata(
            catalogId = "abc123",
            name = "Theme",
            replaces = "replaces-id",
            contributors = contributors,
        )

        val entries = zipEntryNames(DecodingUtils.injectMetadata(emptyZip(), metadata))

        assertTrue(entries.contains("bscm_abc123.json"), "theme manifest entry missing: $entries")
    }

    private fun emptyZip(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("info.json"))
            zip.write("{}".toByteArray())
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    private fun zipEntryNames(zipBytes: ByteArray): List<String> {
        java.util.zip.ZipInputStream(zipBytes.inputStream()).use { zip ->
            return generateSequence { zip.nextEntry }.map { it.name }.toList()
        }
    }
}
