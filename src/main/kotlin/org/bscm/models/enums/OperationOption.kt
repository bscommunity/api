package org.bscm.models.enums

import kotlinx.serialization.Serializable

@Serializable
enum class OperationOption {
    INSTALL,
    UPDATE,
    DELETE,
}