package org.bscm.models.repository

import org.bscm.models.Badge
import org.bscm.models.CatalogItem
import org.bscm.models.User
import org.bscm.models.dto.account.CreateAccountRequest
import org.bscm.models.dto.user.CreateUserRequest
import org.bscm.models.dto.user.SimplifiedUser
import org.bscm.models.dto.user.UpdateUserRequest
import org.bscm.models.dto.user.UserStats
import org.bscm.models.enums.ContentType
import java.util.*

interface IUserRepository {
    suspend fun getUsers(query: String?): List<User>
    suspend fun getUserById(id: UUID): User?
    suspend fun getUserByDiscordId(discordId: String): User?
    suspend fun getUserByUsername(username: String): SimplifiedUser?
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
        contentType: ContentType?,
        query: String?,
        limit: Int,
        offset: Int
    ): List<CatalogItem>

    suspend fun getUserStats(userId: UUID): UserStats

    suspend fun getSystemCollectionItems(
        userId: UUID,
        collectionName: String,
        requestingUserId: UUID?,
        limit: Int
    ): List<CatalogItem>
}
