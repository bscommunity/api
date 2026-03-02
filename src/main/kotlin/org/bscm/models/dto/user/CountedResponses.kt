package org.bscm.models.dto.user

import kotlinx.serialization.Serializable

/**
 * Generic paginated response that pairs a page of items with a total domain count.
 *
 * - [items]  — the current page of items.
 * - [counts] — domain-level totals: Triple(charts, tourPasses, themes) for library / likes / bookmarks
 *              and [Int] for collections
 */
@Serializable
data class ItemsPage<T>(
    val items: List<T>,
    val counts: ContentCounts
)

/** Chart / TourPass / Theme breakdown for library, likes, and bookmarks. */
@Serializable
data class ContentCounts(
    val charts: Int = 0,
    val tourPasses: Int = 0,
    val themes: Int = 0,
    val collections: Int = 0,
) {
}
