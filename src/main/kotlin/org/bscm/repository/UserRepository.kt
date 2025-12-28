package org.bscm.repository

import io.ktor.server.plugins.*
import org.bscm.models.Account
import org.bscm.models.Badge
import org.bscm.models.CatalogItem
import org.bscm.models.User
import org.bscm.models.dao.AccountEntity
import org.bscm.models.dao.UserEntity
import org.bscm.models.dto.account.CreateAccountRequest
import org.bscm.models.dto.user.CreateUserRequest
import org.bscm.models.dto.user.SimplifiedUser
import org.bscm.models.dto.user.UpdateUserRequest
import org.bscm.models.dto.user.UserStats
import org.bscm.models.enums.ContentType
import org.bscm.models.interfaces.*
import org.bscm.models.tables.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

class UserRepository(
    private val chartRepository: IChartRepository,
    private val themeRepository: IThemeRepository,
    private val tourPassRepository: ITourPassRepository,
    private val collectionRepository: ICollectionRepository
) : IUserRepository {
    companion object {
        fun userEntityToUser(entity: UserEntity): User = User(
            id = entity.id.value,
            username = entity.username,
            email = entity.email,
            imageUrl = entity.imageUrl,
            bannerUrl = entity.bannerUrl,
            avatarUrl = entity.avatarUrl,
            accentColor = entity.accentColor,
            bio = entity.bio,
            isPublic = entity.isPublic,

            role = entity.role,
            isVerified = entity.isVerified,
            verifiedAt = entity.verifiedAt,

            discordId = entity.discordId,
            createdAt = entity.createdAt,

            followerCount = entity.followerCount,
            followingCount = entity.followingCount,
        )

        fun userEntityToSimplifiedUser(entity: UserEntity): SimplifiedUser = SimplifiedUser(
            id = entity.id.value,
            username = entity.username,
            imageUrl = entity.imageUrl,
            avatarUrl = entity.avatarUrl,
            bannerUrl = entity.bannerUrl,
            isVerified = entity.isVerified,
            isPublic = entity.isPublic,
            followerCount = entity.followerCount,
            followingCount = entity.followingCount,
            createdAt = entity.createdAt
        )

        fun accountEntityToAccount(entity: AccountEntity): Account = Account(
            id = entity.id.value,
            provider = entity.provider,
            // providerAccountId = entity.providerAccountId,
            refreshToken = entity.refreshToken,
            accessToken = entity.accessToken,
            expiresAt = entity.expiresAt,
            tokenType = entity.tokenType,
            scope = entity.scope,
        )
    }


    override suspend fun getUsers(query: String?): List<User> = newSuspendedTransaction {
        // If query is null, return all users
        if (query.isNullOrBlank()) {
            UserEntity.all().map(::userEntityToUser)
        } else {
            // Otherwise, return users that match the query
            UserEntity.find { UserTable.username like "%$query%" }.map(::userEntityToUser)
        }
    }

    override suspend fun getUserById(id: UUID): User = newSuspendedTransaction {
        UserEntity.findById(id).let {
            it?.let { userEntityToUser(it) }
        } ?: throw NotFoundException("User not found with ID: $id")
    }

    override suspend fun getUserByDiscordId(discordId: String): User? = newSuspendedTransaction {
        UserEntity.find { UserTable.discordId eq discordId }.singleOrNull()?.let(::userEntityToUser)
    }

    override suspend fun getUserByUsername(username: String): SimplifiedUser? = newSuspendedTransaction {
        UserEntity.find { UserTable.username eq username }.singleOrNull()?.let(::userEntityToSimplifiedUser)
    }

    override suspend fun createUser(user: CreateUserRequest): User = newSuspendedTransaction {
        val newUser = UserEntity.new(UUID.randomUUID()) {
            this.username = user.username
            this.email = user.email
            this.discordId = user.discordId
            this.avatarUrl = user.avatarUrl
            this.bannerUrl = user.bannerUrl
            this.accentColor = user.accentColor
        }
        userEntityToUser(newUser)
    }

    override suspend fun updateUser(id: UUID, user: UpdateUserRequest): User = newSuspendedTransaction {
        val existingUser = UserEntity.findById(id) ?: throw NotFoundException("User not found")
        existingUser.apply {
            username = user.username.let { if (it.isNullOrBlank()) username else it }
            email = user.email.let { if (it.isNullOrBlank()) email else it }
            avatarUrl = user.avatarUrl.let { if (it.isNullOrBlank()) avatarUrl else it }
            bannerUrl = user.bannerUrl.let { if (it.isNullOrBlank()) bannerUrl else it }
            accentColor = user.accentColor ?: accentColor
            bio = user.bio.let { if (it.isNullOrBlank()) bio else it }
            isPublic = user.isPublic ?: isPublic
        }
        userEntityToUser(existingUser)
    }

    override suspend fun deleteUser(id: UUID): Boolean = newSuspendedTransaction {
        val user = UserEntity.findById(id) ?: return@newSuspendedTransaction false
        user.delete()
        true
    }

    override suspend fun upsertAccount(id: UUID, account: CreateAccountRequest) = newSuspendedTransaction {
        UserEntity.findById(id) ?: throw NotFoundException("User not found")

        AccountTable.upsert {
            it[provider] = account.provider
            // it[providerAccountId] = account.providerAccountId
            it[refreshToken] = account.refreshToken
            it[accessToken] = account.accessToken
            it[expiresAt] = account.expiresAt
            it[tokenType] = account.tokenType
            it[scope] = account.scope
            it[userId] = id
        }

        true
    }

    override suspend fun deleteAccount(id: UUID): Boolean = newSuspendedTransaction {
        AccountTable.select(AccountTable.id).where { AccountTable.userId eq id }.singleOrNull()
            ?: throw NotFoundException("Account not found for user ID: $id")

        AccountTable.deleteWhere { AccountTable.userId eq id }

        true
    }

    override suspend fun getUserBadges(userId: UUID): List<Badge> = newSuspendedTransaction {
        UserBadgeTable.innerJoin(BadgeTable).selectAll().where {
            UserBadgeTable.userId eq userId
        }.map { row ->
            Badge(
                id = row[BadgeTable.id].value,
                name = row[BadgeTable.name],
                description = row[BadgeTable.description],
                criteria = row[BadgeTable.criteria],
                createdAt = row[BadgeTable.createdAt]
            )
        }
    }

    override suspend fun getUserCharts(
        userId: UUID,
        requestingUserId: UUID?,
        contentType: ContentType?,
        query: String?,
        limit: Int,
        offset: Int
    ): List<CatalogItem> = newSuspendedTransaction {
        // Get content IDs for the user's charts filtered by content type
        val contentQuery = ContentTable
            .innerJoin(ChartTable, { ContentTable.id }, { ChartTable.contentId })
            .select(ContentTable.id, ContentTable.type)
            .where { ChartTable.authorId eq userId }

        // Apply content type filter if specified
        contentType?.let {
            contentQuery.andWhere { ContentTable.type eq it }
        }

        // Apply text search on chart metadata if query is provided
        query?.let { searchQuery ->
            contentQuery.andWhere {
                (ChartTable.artist like "%$searchQuery%") or
                (ChartTable.track like "%$searchQuery%") or
                (ChartTable.album like "%$searchQuery%")
            }
        }

        // Order by latest updated at
        contentQuery.orderBy(ChartTable.latestUpdatedAt to SortOrder.DESC)

        // Apply limit and offset
        val paginatedQuery = contentQuery.limit(limit).offset(offset.toLong())

        val contentIds = paginatedQuery.map { it[ContentTable.id].value }

        if (contentIds.isEmpty()) return@newSuspendedTransaction emptyList()

        // Group by content type and fetch accordingly
        val contentsByType = paginatedQuery.groupBy { it[ContentTable.type] }

        val results = mutableListOf<CatalogItem>()

        // Fetch Charts
        contentsByType[ContentType.CHART]?.let {
            val chartContentIds = it.map { row -> row[ContentTable.id].value }
            val (charts, _) = chartRepository.getCharts(
                userId = requestingUserId,
                contentIds = chartContentIds
            )
            results.addAll(charts)
        }

        // Future: Fetch TourPasses
        contentsByType[ContentType.TOUR_PASS]?.let {
            val tourPassContentIds = it.map { row -> row[ContentTable.id].value }
            val tourPasses = tourPassRepository.getTourPasses(
                userId = requestingUserId,
                contentIds = tourPassContentIds,
                search = null,
                limit = null,
                offset = null
            )
            results.addAll(tourPasses)
        }

        // Future: Fetch Themes
        contentsByType[ContentType.THEME]?.let {
            val themeContentIds = it.map { row -> row[ContentTable.id].value }
            val themes = themeRepository.getThemes(
                contentIds = themeContentIds,
                search = null,
                limit = null,
                offset = null
            )
            results.addAll(themes)
        }

        // Return in order of original query
        val orderMap = contentIds.mapIndexed { index, id -> id to index }.toMap()
        results.sortedBy { orderMap[it.contentId] ?: Int.MAX_VALUE }
    }

    override suspend fun getUserStats(userId: UUID): UserStats = newSuspendedTransaction {
        // Count charts
        val totalCharts = ChartTable
            .select(ChartTable.id)
            .where { ChartTable.authorId eq userId }
            .count()
            .toInt()

        // Count collections (excluding system collections)
        val totalCollections = CollectionTable
            .select(CollectionTable.id)
            .where {
                (CollectionTable.userId eq userId) and
                (CollectionTable.name notInList listOf("likes", "favorites"))
            }
            .count()
            .toInt()

        // Count tour passes (future)
        val totalTourpasses = TourPassTable
            .select(TourPassTable.id)
            .where { TourPassTable.authorId eq userId }
            .count()
            .toInt()

        // Count themes (future)
        val totalThemes = ThemeTable
            .select(ThemeTable.id)
            .where { ThemeTable.authorId eq userId }
            .count()
            .toInt()

        UserStats(
            totalCharts = totalCharts,
            totalCollections = totalCollections,
            totalTourpasses = totalTourpasses,
            totalThemes = totalThemes
        )
    }

    override suspend fun getSystemCollectionItems(
        userId: UUID,
        collectionName: String,
        requestingUserId: UUID?,
        limit: Int
    ): List<CatalogItem> = newSuspendedTransaction {
        // Find the system collection for the user
        val collection = CollectionTable
            .select(CollectionTable.id)
            .where {
                (CollectionTable.userId eq userId) and
                (CollectionTable.name eq collectionName)
            }
            .singleOrNull()
            ?: return@newSuspendedTransaction emptyList()

        val collectionId = collection[CollectionTable.id].value

        // Use the collection repository to get items (respects visibility and proper fetching)
        collectionRepository.getCollectionItems(
            collectionId = collectionId,
            userId = requestingUserId,
            category = null,
            limit = limit,
            offset = 0
        )
    }

    override suspend fun followUser(followerId: UUID, followedId: UUID): Boolean = newSuspendedTransaction {
        // Verify both users exist
        UserEntity.findById(followerId) ?: throw NotFoundException("Follower user not found")
        UserEntity.findById(followedId) ?: throw NotFoundException("User to follow not found")

        // Cannot follow yourself
        if (followerId == followedId) {
            return@newSuspendedTransaction false
        }

        // Check if already following
        val alreadyFollowing = UserFollowTable.selectAll().where {
            (UserFollowTable.follower eq followerId) and (UserFollowTable.followed eq followedId)
        }.empty().not()

        if (alreadyFollowing) {
            return@newSuspendedTransaction false // Already following
        }

        // Insert follow relationship
        UserFollowTable.insert {
            it[follower] = followerId
            it[followed] = followedId
            it[createdAt] = java.time.LocalDateTime.now()
        }

        true
    }

    override suspend fun unfollowUser(followerId: UUID, followedId: UUID): Boolean = newSuspendedTransaction {
        val deletedCount = UserFollowTable.deleteWhere {
            (UserFollowTable.follower eq followerId) and (UserFollowTable.followed eq followedId)
        }

        deletedCount > 0
    }

    override suspend fun getFollowers(userId: UUID, limit: Int, offset: Int): List<SimplifiedUser> = newSuspendedTransaction {
        UserFollowTable
            .innerJoin(UserTable, { UserFollowTable.follower }, { UserTable.id })
            .selectAll()
            .where { UserFollowTable.followed eq userId }
            .orderBy(UserFollowTable.createdAt to SortOrder.DESC)
            .limit(limit)
            .offset(offset.toLong())
            .map { row ->
                val followerUserId = row[UserFollowTable.follower]
                val followerEntity = UserEntity.findById(followerUserId) ?: return@map null
                userEntityToSimplifiedUser(followerEntity)
            }
            .filterNotNull()
    }

    override suspend fun getFollowing(userId: UUID, limit: Int, offset: Int): List<SimplifiedUser> = newSuspendedTransaction {
        UserFollowTable
            .innerJoin(UserTable, { UserFollowTable.followed }, { UserTable.id })
            .selectAll()
            .where { UserFollowTable.follower eq userId }
            .orderBy(UserFollowTable.createdAt to SortOrder.DESC)
            .limit(limit)
            .offset(offset.toLong())
            .map { row ->
                val followedUserId = row[UserFollowTable.followed]
                val followedEntity = UserEntity.findById(followedUserId) ?: return@map null
                userEntityToSimplifiedUser(followedEntity)
            }
            .filterNotNull()
    }

    override suspend fun isFollowing(followerId: UUID, followedId: UUID): Boolean = newSuspendedTransaction {
        UserFollowTable.selectAll().where {
            (UserFollowTable.follower eq followerId) and (UserFollowTable.followed eq followedId)
        }.empty().not()
    }
}


