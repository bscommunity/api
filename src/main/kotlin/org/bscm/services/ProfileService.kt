package org.bscm.services

import io.ktor.server.plugins.*
import org.bscm.models.User
import org.bscm.models.dto.activity.ActivityEntry
import org.bscm.models.dto.activity.ChartActivityItem
import org.bscm.models.dto.user.SimplifiedUser
import org.bscm.models.dto.user.UserProfileResponse
import org.bscm.models.enums.ActivityType
import org.bscm.models.interfaces.IActivityRepository
import org.bscm.models.interfaces.IChartRepository
import org.bscm.models.interfaces.IUserRepository
import org.bscm.repository.ChartRepository
import java.util.*

class ProfileService(
    private val userRepository: IUserRepository,
    private val activityRepository: IActivityRepository,
    private val chartRepository: IChartRepository
) {
    private fun toSimplifiedUser(user: User): SimplifiedUser = SimplifiedUser(
        id = user.id,
        username = user.username,
        avatarUrl = user.avatarUrl,
        bannerUrl = user.bannerUrl,
        bio = user.bio,
        accentColor = user.accentColor,
        isVerified = user.isVerified
    )

    private fun ensureVisibility(user: User, requesterId: UUID?) {
        val isOwner = requesterId == user.id
        if (!isOwner && !user.isPublic) {
            throw NotFoundException("User profile is private")
        }
    }

    private fun filterActivityForViewer(
        items: List<ActivityEntry>,
        isOwner: Boolean,
        isPublic: Boolean
    ): List<ActivityEntry> {
        if (isOwner) return items

        return items.filter { entry ->
            when (entry.type) {
                ActivityType.BOOKMARKED_CHART -> false
                ActivityType.LIKED_CHART -> isPublic
                else -> true
            }
        }
    }

    suspend fun getProfileHeader(userId: UUID, requesterId: UUID?): UserProfileResponse {
        val user = userRepository.getUserById(userId) ?: throw NotFoundException("User not found")
        ensureVisibility(user, requesterId)

        val counts = userRepository.getProfileCounts(user.id)

        return UserProfileResponse(
            user = toSimplifiedUser(user),
            counts = counts,
        )
    }

    suspend fun getActivity(userId: UUID, requesterId: UUID?, limit: Int, offset: Int): List<ActivityEntry> {
        val user = userRepository.getUserById(userId) ?: throw NotFoundException("User not found")
        ensureVisibility(user, requesterId)

        val isOwner = requesterId == user.id
        val items = activityRepository.getUserActivity(user.id, limit, offset)

        val contentIds = items
            .filter { it.type == ActivityType.LIKED_CHART }
            .map { it.targetId }

        val charts = chartRepository.getCharts(filters = ChartRepository.ChartFilters(contentIds = contentIds))
            .first
            .associateBy { it.contentId }

        items.mapNotNull { entry ->
            when (entry.type) {
                ActivityType.LIKED_CHART -> {
                    val chart = charts[entry.targetId] ?: return@mapNotNull null
                    ChartActivityItem(
                        id = entry.id,
                        type = entry.type,
                        createdAt = entry.createdAt,
                        chart = chart
                    )
                }
                else -> null
            }
        }

        return filterActivityForViewer(items, isOwner, user.isPublic)
    }
}
