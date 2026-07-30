package org.bscm.services

import io.ktor.server.plugins.*
import org.bscm.models.User
import org.bscm.models.dto.activity.ActivityItemResponse
import org.bscm.models.dto.activity.ChartActivityItem
import org.bscm.models.dto.activity.ThemeActivityItem
import org.bscm.models.dto.activity.TourPassActivityItem
import org.bscm.models.dto.user.SimplifiedUser
import org.bscm.models.dto.user.UserProfileResponse
import org.bscm.models.enums.ActivityType
import org.bscm.models.interfaces.*
import org.bscm.repository.ChartRepository
import java.util.*

class ProfileService(
    private val userRepository: IUserRepository,
    private val activityRepository: IActivityRepository,
    private val chartRepository: IChartRepository,
    private val tourPassRepository: ITourPassRepository,
    private val themeRepository: IThemeRepository,
) {
    /**
     * Maps a [User] to [SimplifiedUser].
     * When [isOwner] is true the follower/following counts are populated so
     * the profile owner can see their own stats; for other viewers they are null.
     */
    private fun toSimplifiedUser(user: User, isOwner: Boolean): SimplifiedUser = SimplifiedUser(
        id = user.id,
        username = user.username,
        avatarUrl = user.avatarUrl,
        bannerUrl = user.bannerUrl,
        bio = user.bio,
        accentColor = user.accentColor,
        isVerified = user.isVerified,
        followersCount = if (isOwner) user.followerCount else null,
        followingCount = if (isOwner) user.followingCount else null
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
                ActivityType.BOOKMARKED_CHART,
                ActivityType.BOOKMARKED_TOUR_PASS,
                ActivityType.BOOKMARKED_THEME -> false

                ActivityType.LIKED_CHART,
                ActivityType.LIKED_TOUR_PASS,
                ActivityType.LIKED_THEME -> isPublic

                else -> true
            }
        }
    }

    suspend fun getProfileHeader(userId: UUID, requesterId: UUID?): UserProfileResponse {
        val user = userRepository.getUserById(userId) ?: throw NotFoundException("User not found")
        return buildProfileHeader(user, requesterId)
    }

    suspend fun getProfileHeaderByUsername(username: String, requesterId: UUID?): UserProfileResponse {
        val user = userRepository.getUserByUsernameAsFull(username) ?: throw NotFoundException("User not found")
        return buildProfileHeader(user, requesterId)
    }

    private suspend fun buildProfileHeader(user: User, requesterId: UUID?): UserProfileResponse {
        ensureVisibility(user, requesterId)
        val isOwner = requesterId == user.id
        val isFollowing = when {
            requesterId == null || isOwner -> null
            else -> userRepository.isFollowing(requesterId, user.id)
        }
        return UserProfileResponse(
            user = toSimplifiedUser(user, isOwner),
            isFollowing = isFollowing
        )
    }

    suspend fun getActivity(userId: UUID, requesterId: UUID?, limit: Int, offset: Int): List<ActivityItemResponse> {
        val user = userRepository.getUserById(userId) ?: throw NotFoundException("User not found")
        ensureVisibility(user, requesterId)

        val isOwner = requesterId == user.id
        val entries = activityRepository.getUserActivity(user.id, limit, offset)

        val chartTypes = setOf(ActivityType.LIKED_CHART, ActivityType.CREATED_CHART, ActivityType.BOOKMARKED_CHART)
        val tourPassTypes = setOf(ActivityType.LIKED_TOUR_PASS, ActivityType.CREATED_TOUR_PASS, ActivityType.BOOKMARKED_TOUR_PASS)
        val themeTypes = setOf(ActivityType.LIKED_THEME, ActivityType.CREATED_THEME, ActivityType.BOOKMARKED_THEME)

        val chartContentIds = entries
            .filter { it.type in chartTypes }
            .map { it.targetId }
            .distinct()

        val tourPassContentIds = entries
            .filter { it.type in tourPassTypes }
            .map { it.targetId }
            .distinct()

        val themeContentIds = entries
            .filter { it.type in themeTypes }
            .map { it.targetId }
            .distinct()

        val charts = if (chartContentIds.isNotEmpty()) {
            chartRepository.getCharts(filters = ChartRepository.ChartFilters(chartIds = chartContentIds))
                .first
                .associateBy { it.id }
        } else {
            emptyMap()
        }

        val tourPasses = if (tourPassContentIds.isNotEmpty()) {
            tourPassRepository.getTourPasses(userId = requesterId, catalogIds = tourPassContentIds, search = null, limit = null, offset = null)
                .associateBy { it.id }
        } else {
            emptyMap()
        }

        val themes = if (themeContentIds.isNotEmpty()) {
            themeRepository.getThemes(userId = requesterId, catalogIds = themeContentIds, search = null, limit = null, offset = null)
                .associateBy { it.id }
        } else {
            emptyMap()
        }

        val activityItems = entries.mapNotNull { entry ->
            when (entry.type) {
                ActivityType.LIKED_CHART,
                ActivityType.CREATED_CHART,
                ActivityType.BOOKMARKED_CHART -> {
                    val chart = charts[entry.targetId] ?: return@mapNotNull null
                    ChartActivityItem(id = entry.id, type = entry.type, createdAt = entry.createdAt, chart = chart)
                }

                ActivityType.LIKED_TOUR_PASS,
                ActivityType.CREATED_TOUR_PASS,
                ActivityType.BOOKMARKED_TOUR_PASS -> {
                    val tourPass = tourPasses[entry.targetId] ?: return@mapNotNull null
                    TourPassActivityItem(id = entry.id, type = entry.type, createdAt = entry.createdAt, tourPass = tourPass)
                }

                ActivityType.LIKED_THEME,
                ActivityType.CREATED_THEME,
                ActivityType.BOOKMARKED_THEME -> {
                    val theme = themes[entry.targetId] ?: return@mapNotNull null
                    ThemeActivityItem(id = entry.id, type = entry.type, createdAt = entry.createdAt, theme = theme)
                }

                ActivityType.FOLLOWED_USER -> null
            }
        }

        return filterActivityForViewer(activityItems, isOwner, user.isPublic)
    }
}
