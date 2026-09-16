package org.bscm.models.dto.chart

import kotlinx.serialization.Serializable
import org.bscm.models.dto.contributor.SimplifiedContributor

/**
 * Lenient wire format for the `chart` JSON field of `POST /charts` (multipart).
 *
 * The website sends its full [CreateChartPayload][frontend] shape here, which does
 * NOT match [CreateChartRequest]: it uses a singular `genre` key, and sends
 * `coverUrl`/`bpm` as `null` when unknown (both are non-nullable and required on
 * [CreateChartRequest]). Decoding the strict DTO therefore fails and the old route
 * code silently fell back to "no overrides" — dropping `contributors` (as well as
 * `isExplicit`/`audioPreviewUrl`) on every website publish.
 *
 * This DTO only models the fields the route actually consumes. Everything else
 * the website sends is ignored via `ignoreUnknownKeys`, so this decode effectively
 * never fails on a well-formed website payload.
 *
 * [frontend]: website `src/app/services/api/chart.service.ts`
 */
@Serializable
data class ChartPublishOverrides(
    val isExplicit: Boolean? = null,
    val audioPreviewUrl: String? = null,
    val previewVideoId: String? = null,
    val contributors: List<SimplifiedContributor> = emptyList(),
)
