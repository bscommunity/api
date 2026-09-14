package org.bscm.models.dto.chart

import kotlinx.serialization.Serializable

/**
 * Batch chart hydration request.
 *
 * Used by mobile clients to resolve many on-disk chart ids in a single
 * round-trip instead of one `GET /charts/{id}` per chart.
 */
@Serializable
data class BatchChartIdsRequest(
    val ids: List<String> = emptyList(),
)
