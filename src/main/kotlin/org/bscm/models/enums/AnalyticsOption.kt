package org.bscm.models.enums

import kotlinx.serialization.Serializable

@Serializable
enum class AnalyticsOption {
    INSTALL_CONTENT,
    UPDATE_CONTENT,
    DELETE_CONTENT,
    UPDATE_APP,
}