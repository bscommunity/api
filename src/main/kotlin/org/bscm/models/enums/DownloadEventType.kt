package org.bscm.models.enums

import kotlinx.serialization.Serializable

@Serializable
enum class DownloadEventType {
    INSTALL,
    UPDATE
}
