package org.bscm.models.enums

import kotlinx.serialization.Serializable

@Serializable
enum class AnalyticsOption {
    INSTALL,
    UPDATE,
    DELETE,
    APP_UPDATE,
}