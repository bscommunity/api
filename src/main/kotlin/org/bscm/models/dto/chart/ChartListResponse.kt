package org.bscm.models.dto.chart

import kotlinx.serialization.Serializable
import org.bscm.models.Chart

@Serializable
data class ChartListResponse(
    val charts: List<Chart>,
    val total: Int
)

