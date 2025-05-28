package org.bscm.models.tables

import kotlinx.serialization.json.Json
import org.bscm.models.StreamingLink
import org.bscm.models.enums.Difficulty
import org.bscm.models.enums.Genre
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.json.json

object ChartTable : UUIDTable("chart") {
    val artist = varchar("artist", 200).index("idx_chart_artist")
    val track = varchar("track", 200).index("idx_chart_track")
    val album = varchar("album", 200).nullable().index("idx_chart_album")

    val trackUrls = json<List<StreamingLink>>("track_urls", Json.Default)
    val trackPreviewUrl = varchar("track_preview_url", 255).nullable()
    val coverUrl = varchar("cover_url", 255)

    val difficulty = enumerationByName("difficulty", 10, Difficulty::class)
        .index("idx_chart_difficulty")
    val genre = enumerationByName("genres", 20, Genre::class).nullable()
        .index("idx_chart_genre")

    val isDeluxe = bool("is_deluxe").default(false)
    val isExplicit = bool("is_explicit").default(false)
    val isFeatured = bool("is_featured").default(false)
        .index("idx_chart_featured") // Featured charts are frequently queried
    val isPublic = bool("is_public").default(true)
        .index("idx_chart_public") // Critical for access control filtering

    val latestVersionId = reference("latest_version_id", VersionTable, onDelete = ReferenceOption.SET_NULL)
        .nullable()
        .index("idx_chart_latest_version")

    // Composite indexes for common query patterns
    init {
        // For search queries combining multiple text fields
        index("idx_chart_search_composite", false, artist, track, album)

        // For filtering by access and status
        index("idx_chart_access_filter", false, isPublic, isFeatured)

        // For filtering by metadata characteristics
        index("idx_chart_metadata_filter", false, difficulty, genre, isPublic)

        // For search with difficulty filtering (common pattern)
        index("idx_chart_search_difficulty", false, difficulty, artist, track)

        // For public featured content (homepage queries)
        index("idx_chart_public_featured", false, isPublic, isFeatured, difficulty)

        index("idx_chart_latest_version_id", false, latestVersionId)
    }
}