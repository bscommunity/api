package org.bscm.models.interfaces

import org.bscm.models.Badge
import org.bscm.models.CatalogItem
import org.bscm.models.User
import org.bscm.models.dto.account.CreateAccountRequest
import org.bscm.models.dto.user.CreateUserRequest
import org.bscm.models.dto.user.SimplifiedUser
import org.bscm.models.dto.user.UpdateUserRequest
import org.bscm.models.dto.user.UserProfileCounts
import org.bscm.models.enums.*
import java.util.*

interface IUserRepository {
    suspend fun getUsers(query: String?): List<User>
    suspend fun getUserById(id: UUID): User?
    suspend fun getUserByDiscordId(discordId: String): User?
    suspend fun getUserByUsername(username: String): SimplifiedUser?
    suspend fun getUserByUsernameAsFull(username: String): User?
    suspend fun createUser(user: CreateUserRequest): User
    suspend fun updateUser(id: UUID, user: UpdateUserRequest): User
    suspend fun deleteUser(id: UUID): Boolean
    suspend fun upsertAccount(id: UUID, account: CreateAccountRequest): Boolean
    suspend fun deleteAccount(id: UUID): Boolean

    // Profile-specific methods
    suspend fun getUserBadges(userId: UUID): List<Badge>

    suspend fun getUserCharts(
        userId: UUID,
        requestingUserId: UUID?,
        query: String?,
        limit: Int,
        offset: Int
    ): List<CatalogItem>

    suspend fun getUserTourPasses(
        userId: UUID,
        requestingUserId: UUID?,
        query: String?,
        sortBy: SortOption? = null,
        limit: Int,
        offset: Int
    ): List<CatalogItem>

    suspend fun getUserThemes(
        userId: UUID,
        requestingUserId: UUID?,
        query: String?,
        limit: Int,
        offset: Int
    ): List<CatalogItem>

    suspend fun getProfileCounts(userId: UUID, followerCount: Int, followingCount: Int, requestedCounts: Set<String> = emptySet()): UserProfileCounts

    suspend fun getUserUploads(
        userId: UUID,
        types: List<CatalogItemType>?,
        query: String?,
        sortBy: SortOption?,
        genres: List<Genre>? = null,
        difficulties: List<Difficulty>? = null,
        isDeluxe: Boolean? = null,
        limit: Int,
        offset: Int,
        includeVersions: Boolean = false,
    ): Pair<List<CatalogItem>, Triple<Int, Int, Int>>

    suspend fun getUserSharedUploads(
        userId: UUID,
        types: List<CatalogItemType>?,
        query: String?,
        sortBy: SortOption?,
        genres: List<Genre>? = null,
        difficulties: List<Difficulty>? = null,
        isDeluxe: Boolean? = null,
        limit: Int,
        offset: Int,
        includeVersions: Boolean = false,
    ): Pair<List<CatalogItem>, Triple<Int, Int, Int>>

    suspend fun getLibraryCounts(userId: UUID): Triple<Int, Int, Int>

    suspend fun getSystemCollectionItems(
        userId: UUID,
        collectionKind: CollectionKind,
        requestingUserId: UUID?,
        limit: Int
    ): List<CatalogItem>

    // Following/Followers methods
    suspend fun followUser(followerId: UUID, followedId: UUID): Boolean
    suspend fun unfollowUser(followerId: UUID, followedId: UUID): Boolean
    suspend fun getFollowers(userId: UUID, limit: Int, offset: Int): List<SimplifiedUser>
    suspend fun getFollowing(userId: UUID, limit: Int, offset: Int): List<SimplifiedUser>
    suspend fun isFollowing(followerId: UUID, followedId: UUID): Boolean

    // Contributor invite policy
    suspend fun getContributorInvitePolicies(userIds: List<UUID>): Map<UUID, ContributorInvitePolicy>
}
