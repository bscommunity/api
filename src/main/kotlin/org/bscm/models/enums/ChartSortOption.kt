package org.bscm.models.enums

import kotlinx.serialization.Serializable

@Serializable
enum class ChartSortOption {
    WEEKLY_RANK,
    LAST_UPDATED,
    MOST_DOWNLOADED,
    MOST_LIKED
}