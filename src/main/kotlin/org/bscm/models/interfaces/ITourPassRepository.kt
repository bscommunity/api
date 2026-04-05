package org.bscm.models.interfaces

import org.bscm.models.TourPass
import java.util.*

interface ITourPassRepository {
    suspend fun getTourPasses(
        userId: UUID? = null,
        contentIds: List<String>?,
        search: String?,
        limit: Int? = null,
        offset: Int? = null,
    ): List<TourPass>

    suspend fun getTourPassById(id: ULong, userId: UUID? = null): TourPass?
    suspend fun getAppTourPassById(contentId: String, userId: UUID? = null): TourPass?
    suspend fun createTourPass(
        userId: UUID,
        name: String,
        description: String?,
        artist: String?,
        coverUrl: String,
        chartIds: List<ULong>? = null,
        id: ULong? = null,
    ): TourPass
    suspend fun updateTourPass(id: ULong, userId: UUID, name: String?, description: String?, artist: String?, coverUrl: String?, chartIds: List<ULong>? = null): TourPass
    suspend fun deleteTourPass(id: ULong, userId: UUID): Boolean
    suspend fun setTourPassCharts(id: ULong, userId: UUID, chartIds: List<ULong>): TourPass
    suspend fun addChartToTourPass(tourPassId: ULong, chartId: ULong): Boolean
    suspend fun removeChartFromTourPass(tourPassId: ULong, chartId: ULong): Boolean
}
