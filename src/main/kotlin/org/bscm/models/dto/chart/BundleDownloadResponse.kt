package org.bscm.models.dto.chart

import kotlinx.serialization.Serializable

@Serializable
data class BundleDownloadResponse(
    val url: String,
)
