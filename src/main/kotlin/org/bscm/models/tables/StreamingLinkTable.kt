package org.bscm.models.tables

import org.bscm.models.enums.StreamingPlatform
import org.jetbrains.exposed.dao.id.UUIDTable

object StreamingLinkTable : UUIDTable("streaming_link") {
    val platform = enumerationByName("platform", 20, StreamingPlatform::class)
    val url = varchar("url", 512).uniqueIndex()

    init {
        index(true, platform, url) // Enforce uniqueness by platform+url
    }
}