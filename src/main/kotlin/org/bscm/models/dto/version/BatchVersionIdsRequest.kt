package org.bscm.models.dto.version

import kotlinx.serialization.Serializable

/**
 * Batch latest-version lookup request.
 *
 * Used by mobile clients to check for updates on many installed items
 * (charts, themes) in a single round-trip instead of one request per
 * content type.
 */
@Serializable
data class BatchVersionIdsRequest(
    val ids: List<String> = emptyList(),
)
