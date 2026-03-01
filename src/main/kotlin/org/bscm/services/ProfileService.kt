package org.bscm.services

import io.ktor.server.plugins.*
import org.bscm.models.User
import org.bscm.models.dto.activity.ActivityItemResponse
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
        items: List<ActivityItemResponse>,
        isOwner: Boolean,
        isPublic: Boolean
    ): List<ActivityItemResponse> {
        if (isOwner) return items

        return items.filter { entry ->
            when (entry.type) {
                ActivityType.BOOKMARKED_CHART -> false
                ActivityType.LIKED_CHART -> isPublic
                else -> true
            }
        }
    }

    suspend fun getProfileHeader(userId: UUID, requesterId: UUID?, requestedCounts: Set<String> = emptySet()): UserProfileResponse {
        val user = userRepository.getUserById(userId) ?: throw NotFoundException("User not found")
        return buildProfileHeader(user, requesterId, requestedCounts)
    }

    suspend fun getProfileHeaderByUsername(username: String, requesterId: UUID?, requestedCounts: Set<String> = emptySet()): UserProfileResponse {
        val user = userRepository.getUserByUsernameAsFull(username) ?: throw NotFoundException("User not found")
        return buildProfileHeader(user, requesterId, requestedCounts)
    }

    private suspend fun buildProfileHeader(user: User, requesterId: UUID?, requestedCounts: Set<String> = emptySet()): UserProfileResponse {
        ensureVisibility(user, requesterId)
        val counts = userRepository.getProfileCounts(user.id, user.followerCount, user.followingCount, requestedCounts)
        val isFollowing = when {
            requesterId == null || requesterId == user.id -> null
            else -> userRepository.isFollowing(requesterId, user.id)
        }
        return UserProfileResponse(
            user = toSimplifiedUser(user),
            isFollowing = isFollowing,
            counts = counts
        )
    }

    suspend fun getActivity(userId: UUID, requesterId: UUID?, limit: Int, offset: Int): List<ActivityItemResponse> {
        val user = userRepository.getUserById(userId) ?: throw NotFoundException("User not found")
        ensureVisibility(user, requesterId)

        val isOwner = requesterId == user.id
        val entries = activityRepository.getUserActivity(user.id, limit, offset)

        // Group entries by activity type and collect target IDs
        val chartContentIds = entries
            .filter { it.type == ActivityType.LIKED_CHART || it.type == ActivityType.CREATED_CHART || it.type == ActivityType.BOOKMARKED_CHART }
            .map { it.targetId }
            .distinct()

        // Fetch all required content objects
        val charts = if (chartContentIds.isNotEmpty()) {
            chartRepository.getCharts(filters = ChartRepository.ChartFilters(contentIds = chartContentIds))
                .first
                .associateBy { it.contentId }
        } else {
            emptyMap()
        }

        // Transform entries to ActivityItemResponse with full content objects
        val activityItems = entries.mapNotNull { entry ->
            when (entry.type) {
                ActivityType.LIKED_CHART,
                ActivityType.CREATED_CHART,
                ActivityType.BOOKMARKED_CHART -> {
                    val chart = charts[entry.targetId] ?: return@mapNotNull null
                    ChartActivityItem(
                        id = entry.id,
                        type = entry.type,
                        createdAt = entry.createdAt,
                        chart = chart
                    )
                }
                ActivityType.FOLLOWED_USER -> {
                    // TODO: Implement UserActivityItem when needed
                    null
                }
            }
        }

        return filterActivityForViewer(activityItems, isOwner, user.isPublic)
    }
}
