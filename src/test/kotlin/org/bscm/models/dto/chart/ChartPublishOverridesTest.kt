package org.bscm.models.dto.chart

import org.bscm.models.enums.ContributorRole
import org.bscm.services.track.clients.jsonClient
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Guards the `chart` JSON field of `POST /charts` against the website payload shape.
 *
 * The website sends its full `CreateChartPayload` (singular `genre`, nullable
 * `coverUrl`/`bpm`), which the strict [CreateChartRequest] rejects — the route used
 * to decode that DTO and silently fall back to "no overrides", dropping the author's
 * chosen contributors on every publish. The route now decodes [ChartPublishOverrides],
 * which must accept the website shape verbatim.
 */
class ChartPublishOverridesTest {

    // Mirrors website `ChartService.createChart`: coverUrl/bpm null when unknown,
    // singular `genre` key, contributors inline.
    private val websitePayload = """
        {
          "artist": "Artist Name",
          "track": "Track Title",
          "album": null,
          "trackUrls": [],
          "previewUrl": null,
          "trackPreviewUrl": null,
          "coverUrl": null,
          "genre": null,
          "isExplicit": true,
          "duration": 187,
          "notesAmount": 512,
          "effectsAmount": 64,
          "bpm": null,
          "difficulty": "HARD",
          "isDeluxe": false,
          "contributors": [
            {"userId": "123e4567-e89b-12d3-a456-426614174000", "roles": ["CHART", "AUDIO"]},
            {"userId": "123e4567-e89b-12d3-a456-426614174001", "roles": ["AUDIO"]}
          ]
        }
    """.trimIndent()

    @Test
    fun `website payload decodes with contributors intact`() {
        val overrides = jsonClient.decodeFromString<ChartPublishOverrides>(websitePayload)

        assertEquals(true, overrides.isExplicit)
        assertNull(overrides.previewUrl)
        assertEquals(2, overrides.contributors.size)
        assertEquals(
            UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
            overrides.contributors[0].userId,
        )
        assertEquals(listOf(ContributorRole.CHART, ContributorRole.AUDIO), overrides.contributors[0].roles)
        assertEquals(listOf(ContributorRole.AUDIO), overrides.contributors[1].roles)
    }

    @Test
    fun `website payload is rejected by the strict DTO (why lenient parse exists)`() {
        val fails = runCatching {
            jsonClient.decodeFromString<CreateChartRequest>(websitePayload)
        }.isFailure

        assertTrue(fails, "Expected strict CreateChartRequest decode to fail on website payload")
    }

    @Test
    fun `absent contributors default to empty list`() {
        val overrides = jsonClient.decodeFromString<ChartPublishOverrides>("{}")

        assertEquals(emptyList(), overrides.contributors)
        assertNull(overrides.isExplicit)
        assertNull(overrides.previewUrl)
    }
}
