package org.bscm.models.dto.changelog

import kotlinx.serialization.Serializable

@Serializable
data class CreateChangelogEntryRequest(
    val description: String
)