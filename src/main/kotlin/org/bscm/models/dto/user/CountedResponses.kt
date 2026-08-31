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
    val counts: CatalogCounts? = null
)

/** Chart / TourPass / Theme breakdown for library, likes, and bookmarks. */
@Serializable
data class CatalogCounts(
    val charts: Int = 0,
    val tourPasses: Int = 0,
    val themes: Int = 0,
    val collections: Int = 0,
) {
}

/**
 * Uniform paginated response for public content listings.
 * Pairs a page of items with an optional total count (present when `count=true`).
 */
@Serializable
data class PagedResponse<T>(
    val items: List<T>,
    val total: Int? = null,
)