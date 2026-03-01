package org.bscm.models.dto.user

import kotlinx.serialization.Serializable
import org.bscm.models.Collection

/**
 * Generic paginated response that pairs a page of items with a total domain count.
 *
 * - [items]  — the current page of items.
 * - [counts] — domain-level totals: Triple(charts, tourPasses, themes) for
 *              library / likes / bookmarks; a single Int wrapped as Triple(n,0,0)
 *              is not used — collections use [CollectionsPage] instead.
 */
@Serializable
data class ItemsPage<T>(
    val items: List<T>,
    val counts: ContentCounts
)

/** Chart / TourPass / Theme breakdown for library, likes, and bookmarks. */
@Serializable
data class ContentCounts(
    val charts: Int,
    val tourPasses: Int,
    val themes: Int
)

/** Collections page: items + total number of collections the user owns. */
@Serializable
data class CollectionsPage(
    val items: List<Collection>,
    val total: Int
)

