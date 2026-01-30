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
import org.bscm.models.dto.user.UserProfileCounts
import org.bscm.models.enums.CollectionKind
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
        query: String?,
        limit: Int,
        offset: Int
    ): List<CatalogItem> = newSuspendedTransaction {
        // Get content IDs for the user's charts
        val contentQuery = ContentTable
            .innerJoin(ChartTable, { ContentTable.id }, { ChartTable.contentId })
            .select(ContentTable.id, ContentTable.type)
            .where { ChartTable.authorId eq userId }

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

        // Fetch Charts
        val (charts, _) = chartRepository.getCharts(
            userId = requestingUserId,
            contentIds = contentIds
        )

        // Return in order of original query
        val orderMap = contentIds.mapIndexed { index, id -> id to index }.toMap()
        charts.sortedBy { orderMap[it.contentId] ?: Int.MAX_VALUE }
    }

    override suspend fun getUserTourPasses(
        userId: UUID,
        requestingUserId: UUID?,
        query: String?,
        limit: Int,
        offset: Int
    ): List<CatalogItem> = newSuspendedTransaction {
        // Get content IDs for the user's tour passes
        val contentQuery = ContentTable
            .innerJoin(TourPassTable, { ContentTable.id }, { TourPassTable.contentId })
            .select(ContentTable.id, ContentTable.type)
            .where { TourPassTable.authorId eq userId }

        // Apply text search on tour pass metadata if query is provided
        query?.let { searchQuery ->
            contentQuery.andWhere {
                (TourPassTable.name like "%$searchQuery%") or
                (TourPassTable.artist like "%$searchQuery%")
            }
        }

        // Order by latest updated at
        contentQuery.orderBy(TourPassTable.latestUpdatedAt to SortOrder.DESC)

        // Apply limit and offset
        val paginatedQuery = contentQuery.limit(limit).offset(offset.toLong())

        val contentIds = paginatedQuery.map { it[ContentTable.id].value }

        if (contentIds.isEmpty()) return@newSuspendedTransaction emptyList()

        // Fetch TourPasses
        val tourPasses = tourPassRepository.getTourPasses(
            userId = requestingUserId,
            contentIds = contentIds,
            search = null,
            limit = null,
            offset = null
        )

        // Return in order of original query
        val orderMap = contentIds.mapIndexed { index, id -> id to index }.toMap()
        tourPasses.sortedBy { orderMap[it.contentId] ?: Int.MAX_VALUE }
    }

    override suspend fun getUserThemes(
        userId: UUID,
        requestingUserId: UUID?,
        query: String?,
        limit: Int,
        offset: Int
    ): List<CatalogItem> = newSuspendedTransaction {
        // Get content IDs for the user's themes
        val contentQuery = ContentTable
            .innerJoin(ThemeTable, { ContentTable.id }, { ThemeTable.contentId })
            .select(ContentTable.id, ContentTable.type)
            .where { ThemeTable.authorId eq userId }

        // Apply text search on theme metadata if query is provided
        query?.let { searchQuery ->
            contentQuery.andWhere {
                (ThemeTable.name like "%$searchQuery%") or
                (ThemeTable.replaces like "%$searchQuery%")
            }
        }

        // Order by latest updated at
        contentQuery.orderBy(ThemeTable.latestUpdatedAt to SortOrder.DESC)

        // Apply limit and offset
        val paginatedQuery = contentQuery.limit(limit).offset(offset.toLong())

        val contentIds = paginatedQuery.map { it[ContentTable.id].value }

        if (contentIds.isEmpty()) return@newSuspendedTransaction emptyList()

        // Fetch Themes
        val themes = themeRepository.getThemes(
            contentIds = contentIds,
            search = null,
            limit = null,
            offset = null
        )

        // Return in order of original query
        val orderMap = contentIds.mapIndexed { index, id -> id to index }.toMap()
        themes.sortedBy { orderMap[it.contentId] ?: Int.MAX_VALUE }
    }

    override suspend fun getProfileCounts(userId: UUID): UserProfileCounts = newSuspendedTransaction {
        // Count charts
        val totalCharts = ChartTable
            .select(ChartTable.id)
            .where { ChartTable.authorId eq userId }
            .count()
            .toInt()

        // Count collections (only USER kind collections)
        val totalCollections = CollectionTable
            .select(CollectionTable.id)
            .where {
                (CollectionTable.userId eq userId) and
                (CollectionTable.kind eq CollectionKind.USER)
            }
            .count()
            .toInt()

        // Count likes (from LIKES system collection)
        val likesCollection = CollectionTable
            .select(CollectionTable.id)
            .where {
                (CollectionTable.userId eq userId) and
                (CollectionTable.kind eq CollectionKind.LIKES)
            }
            .singleOrNull()

        val totalLikes = likesCollection?.let {
            CollectionItemTable
                .select(CollectionItemTable.id)
                .where { CollectionItemTable.collectionId eq it[CollectionTable.id] }
                .count()
                .toInt()
        } ?: 0

        // Count bookmarks (from BOOKMARKS system collection)
        val bookmarksCollection = CollectionTable
            .select(CollectionTable.id)
            .where {
                (CollectionTable.userId eq userId) and
                (CollectionTable.kind eq CollectionKind.BOOKMARKS)
            }
            .singleOrNull()

        val totalBookmarks = bookmarksCollection?.let {
            CollectionItemTable
                .select(CollectionItemTable.id)
                .where { CollectionItemTable.collectionId eq it[CollectionTable.id] }
                .count()
                .toInt()
        } ?: 0

        UserProfileCounts(
            charts = totalCharts,
            likes = totalLikes,
            bookmarks = totalBookmarks,
            collections = totalCollections
        )
    }

    override suspend fun getSystemCollectionItems(
        userId: UUID,
        collectionKind: CollectionKind,
        requestingUserId: UUID?,
        limit: Int
    ): List<CatalogItem> = newSuspendedTransaction {
        require(collectionKind != CollectionKind.USER) {
            "Cannot get system collection items for USER kind"
        }

        // Find the system collection for the user by kind
        val collection = CollectionTable
            .select(CollectionTable.id)
            .where {
                (CollectionTable.userId eq userId) and
                (CollectionTable.kind eq collectionKind)
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


