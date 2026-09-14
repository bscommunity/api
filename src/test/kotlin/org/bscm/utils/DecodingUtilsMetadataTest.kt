package org.bscm.utils

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guards the `bscm.json` bundle metadata both publish services inject.
 *
 * The Android app parses this file ([MetadataParser]) and renders its
 * `contributors` array as "The following users contributed to this chart".
 * An author-only array here means mobile credits only the author, no matter
 * what rows exist in the API database — so creation must embed every initial
 * contributor with an Android-compatible lowercase role name.
 */
class DecodingUtilsMetadataTest {

    private val contributors = listOf(
        DecodingUtils.MetadataContributor(
            username = "authorUser",
            avatarUrl = "https://example.com/author.png",
            role = "author",
        ),
        DecodingUtils.MetadataContributor(
            username = "charterUser",
            avatarUrl = null,
            role = "chart",
        ),
        DecodingUtils.MetadataContributor(
            username = "audioUser",
            avatarUrl = "https://example.com/audio.png",
            role = "audio",
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

        assertTrue(json.contains("\"username\": \"authorUser\""), "author missing:\n$json")
        assertTrue(json.contains("\"username\": \"charterUser\""), "charter missing:\n$json")
        assertTrue(json.contains("\"username\": \"audioUser\""), "audio contributor missing:\n$json")
        assertTrue(json.contains("\"role\": \"chart\""), "lowercase role missing:\n$json")
    }

    @Test
    fun `theme metadata embeds every initial contributor`() {
        val json = DecodingUtils.ThemeMetadata(
            catalogId = "abc123",
            name = "Theme",
            replaces = "replaces-id",
            contributors = contributors,
        ).toJson()

        assertTrue(json.contains("\"username\": \"authorUser\""), "author missing:\n$json")
        assertTrue(json.contains("\"username\": \"charterUser\""), "charter missing:\n$json")
        assertTrue(json.contains("\"username\": \"audioUser\""), "audio contributor missing:\n$json")
    }
}
