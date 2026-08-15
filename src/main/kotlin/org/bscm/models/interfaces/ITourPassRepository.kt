package org.bscm.models.interfaces

import org.bscm.models.StreamingRef
import org.bscm.models.TourPass
import java.util.*

interface ITourPassRepository {
    suspend fun getTourPasses(
        userId: UUID? = null,
        catalogIds: List<String>? = null,
        search: String? = null,
        limit: Int? = null,
        offset: Int? = null,
    ): List<TourPass>

    suspend fun getTourPassById(id: String, userId: UUID? = null): TourPass?
    suspend fun createTourPass(
        userId: UUID,
        name: String,
        description: String?,
        artist: String?,
        playlistUrls: List<StreamingRef>? = null,
        chartIds: List<String>? = null,
        id: String? = null,
    ): TourPass

    suspend fun updateTourPass(
        id: String,
        userId: UUID,
        name: String?,
        description: String?,
        artist: String?,
        chartIds: List<String>? = null,
    ): TourPass

    suspend fun deleteTourPass(id: String, userId: UUID): Boolean
    suspend fun setTourPassCharts(id: String, userId: UUID, chartIds: List<String>): TourPass
    suspend fun addChartToTourPass(tourPassId: String, chartId: String): Boolean
    suspend fun removeChartFromTourPass(tourPassId: String, chartId: String): Boolean
    suspend fun countTourPasses(search: String? = null): Int
    suspend fun updateDiscordCoordinates(catalogItemId: String, channelId: String, messageId: String)
}
