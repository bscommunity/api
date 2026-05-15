package org.bscm.models.enums

import kotlinx.serialization.Serializable

@Serializable
enum class CatalogItemStatus {
    DRAFT,
    PROCESSING,
    PUBLISHED,
    REJECTED,
    ARCHIVED
}