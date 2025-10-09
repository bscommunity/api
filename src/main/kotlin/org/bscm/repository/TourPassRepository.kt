package org.bscm.repository

import org.bscm.models.TourPass
import java.util.*

interface TourPassRepository {
    suspend fun getTourPasses(
        userId: UUID? = null,
        contentIds: List<String>?,
        search: String?,
        limit: Int? = null,
        offset: Int? = null,
    ): List<TourPass>

    suspend fun getTourPassById(id: ULong): TourPass?
    suspend fun getAppTourPassById(contentId: String): TourPass?
    suspend fun createTourPass(userId: UUID, name: String, artist: String?, coverUrl: String): TourPass
    suspend fun updateTourPass(id: ULong, name: String?, artist: String?, coverUrl: String?): TourPass
    suspend fun deleteTourPass(id: ULong): Boolean
    suspend fun addChartToTourPass(tourPassId: ULong, chartId: ULong): Boolean
    suspend fun removeChartFromTourPass(tourPassId: ULong, chartId: ULong): Boolean
}
