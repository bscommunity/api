package org.bscm.models

import kotlinx.serialization.Serializable

@Serializable
data class StreamingLink(val platform: String, val url: String)