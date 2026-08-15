package org.bscm.models.enums

import kotlinx.serialization.Serializable

@Serializable
enum class SortOption {
    WEEKLY_RANK,
    LAST_UPDATED,
    MOST_DOWNLOADED,
    MOST_LIKED,
    ALPHA_ASC,
    ALPHA_DESC
}