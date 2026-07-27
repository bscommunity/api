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
import org.bscm.models.enums.CatalogItemType
import org.bscm.models.enums.CollectionKind
import org.bscm.models.enums.SortOption
import org.bscm.models.interfaces.*
import org.bscm.models.tables.*
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
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
            avatarUrl = entity.avatarUrl,
            bannerUrl = entity.bannerUrl,
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
            avatarUrl = entity.avatarUrl,
            bannerUrl = entity.bannerUrl,
            isVerified = entity.isVerified,
            bio = entity.bio,
            accentColor = entity.accentColor
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


    override suspend fun getUsers(query: String?): List<User> = suspendTransaction {
        // If query is null, return all users
        if (query.isNullOrBlank()) {
            UserEntity.all().map(::userEntityToUser)
        } else {
            // Otherwise, return users that match the query
            UserEntity.find { UserTable.username like "%$query%" }.map(::userEntityToUser)
        }
    }

    override suspend fun getUserById(id: UUID): User? = suspendTransaction {
        UserEntity.findById(id)?.let { userEntityToUser(it) }
    }

    override suspend fun getUserByDiscordId(discordId: String): User? = suspendTransaction {
        UserEntity.find { UserTable.discordId eq discordId }.singleOrNull()?.let(::userEntityToUser)
    }

    override suspend fun getUserByUsername(username: String): SimplifiedUser? = suspendTransaction {
        UserEntity.find { UserTable.username eq username }.singleOrNull()?.let(::userEntityToSimplifiedUser)
    }

    override suspend fun getUserByUsernameAsFull(username: String): User? = suspendTransaction {
        UserEntity.find { UserTable.username eq username }.singleOrNull()?.let(::userEntityToUser)
    }

    override suspend fun createUser(user: CreateUserRequest): User = suspendTransaction {
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

    override suspend fun updateUser(id: UUID, user: UpdateUserRequest): User = suspendTransaction {
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

    override suspend fun deleteUser(id: UUID): Boolean = suspendTransaction {
        val user = UserEntity.findById(id) ?: return@suspendTransaction false
        user.delete()
        true
    }

    override suspend fun upsertAccount(id: UUID, account: CreateAccountRequest) = suspendTransaction {
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

    override suspend fun deleteAccount(id: UUID): Boolean = suspendTransaction {
        AccountTable.select(AccountTable.id).where { AccountTable.userId eq id }.singleOrNull()
            ?: throw NotFoundException("Account not found for user ID: $id")

        AccountTable.deleteWhere { AccountTable.userId eq id }

        true
    }

    override suspend fun getUserBadges(userId: UUID): List<Badge> = suspendTransaction {
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
    ): List<CatalogItem> = suspendTransaction {
        // Get content IDs for the user's charts
        val contentQuery = CatalogItemTable
            .innerJoin(ChartTable, { CatalogItemTable.id }, { ChartTable.id })
            .innerJoin(TrackTable, { ChartTable.trackId }, { TrackTable.id })
            .leftJoin(AlbumTable, { TrackTable.albumId }, { AlbumTable.id })
            .select(CatalogItemTable.id, CatalogItemTable.type)
            .where { CatalogItemTable.authorId eq userId }

        // Apply text search on chart metadata if query is provided
        query?.let { searchQuery ->
            contentQuery.andWhere {
                (TrackTable.artist like "%$searchQuery%") or
                (TrackTable.title like "%$searchQuery%") or
                (AlbumTable.name like "%$searchQuery%")
            }
        }

        // Order by latest updated at
        contentQuery.orderBy(CatalogItemTable.updatedAt to SortOrder.DESC)

        // Apply limit and offset
        val paginatedQuery = contentQuery.limit(limit).offset(offset.toLong())

        val contentIds = paginatedQuery.map { it[CatalogItemTable.id].value }

        if (contentIds.isEmpty()) return@suspendTransaction emptyList()

        // Fetch Charts
        val (charts, _) = chartRepository.getCharts(
            filters = ChartRepository.ChartFilters(
                chartIds = contentIds,
                includePrivate = requestingUserId == userId
            )
        )

        // Return in order of original query
        val orderMap = contentIds.mapIndexed { index, id -> id to index }.toMap()
        charts.sortedBy { orderMap[it.id] ?: Int.MAX_VALUE }
    }

    override suspend fun getUserTourPasses(
        userId: UUID,
        requestingUserId: UUID?,
        query: String?,
        sortBy: SortOption?,
        limit: Int,
        offset: Int
    ): List<CatalogItem> = suspendTransaction {
        // Get content IDs for the user's tour passes
        val contentQuery = CatalogItemTable
            .innerJoin(TourPassTable, { CatalogItemTable.id }, { TourPassTable.id })
            .select(CatalogItemTable.id, CatalogItemTable.type)
            .where { CatalogItemTable.authorId eq userId }

        // Apply text search on tour pass metadata if query is provided
        query?.let { searchQuery ->
            contentQuery.andWhere {
                (TourPassTable.name like "%$searchQuery%") or
                (TourPassTable.description like "%$searchQuery%")
            }
        }

        // Apply sorting
        when (sortBy) {
            SortOption.MOST_DOWNLOADED -> contentQuery.orderBy(CatalogItemTable.downloadsSum to SortOrder.DESC)
            SortOption.ALPHA_ASC -> contentQuery.orderBy(TourPassTable.name to SortOrder.ASC)
            SortOption.ALPHA_DESC -> contentQuery.orderBy(TourPassTable.name to SortOrder.DESC)
            SortOption.LAST_UPDATED, SortOption.WEEKLY_RANK, SortOption.MOST_LIKED, null ->
                contentQuery.orderBy(CatalogItemTable.updatedAt to SortOrder.DESC)
        }

        // Apply limit and offset
        val paginatedQuery = contentQuery.limit(limit).offset(offset.toLong())

        val contentIds = paginatedQuery.map { it[CatalogItemTable.id].value }

        if (contentIds.isEmpty()) return@suspendTransaction emptyList()

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
        tourPasses.sortedBy { orderMap[it.id] ?: Int.MAX_VALUE }
    }

    override suspend fun getUserThemes(
        userId: UUID,
        requestingUserId: UUID?,
        query: String?,
        limit: Int,
        offset: Int
    ): List<CatalogItem> = suspendTransaction {
        // Get content IDs for the user's themes
        val contentQuery = CatalogItemTable
            .innerJoin(ThemeTable, { CatalogItemTable.id }, { ThemeTable.id })
            .select(CatalogItemTable.id, CatalogItemTable.type)
            .where { CatalogItemTable.authorId eq userId }

        // Apply text search on theme metadata if query is provided
        query?.let { searchQuery ->
            contentQuery.andWhere {
                (ThemeTable.name like "%$searchQuery%") or
                (ThemeTable.replaces like "%$searchQuery%")
            }
        }

        // Order by latest updated at
        contentQuery.orderBy(CatalogItemTable.updatedAt to SortOrder.DESC)

        // Apply limit and offset
        val paginatedQuery = contentQuery.limit(limit).offset(offset.toLong())

        val contentIds = paginatedQuery.map { it[CatalogItemTable.id].value }

        if (contentIds.isEmpty()) return@suspendTransaction emptyList()

        // Fetch Themes
        val themes = themeRepository.getThemes(
            userId = requestingUserId,
            contentIds = contentIds,
            search = null,
            limit = null,
            offset = null
        )

        // Return in order of original query
        val orderMap = contentIds.mapIndexed { index, id -> id to index }.toMap()
        themes.sortedBy { orderMap[it.id] ?: Int.MAX_VALUE }
    }

    override suspend fun getProfileCounts(userId: UUID, followerCount: Int, followingCount: Int, requestedCounts: Set<String>): UserProfileCounts = suspendTransaction {
        val all = requestedCounts.isEmpty()

        // Helper: count items in a system collection broken down by content type -> Triple(charts, tourPasses, themes)
        fun countByKind(kind: CollectionKind): Triple<Int, Int, Int> {
            val countColumn = CollectionItemTable.id.count()
            val rows = CollectionItemTable
                .innerJoin(CollectionTable, { CollectionItemTable.collectionId }, { CollectionTable.id })
                .innerJoin(CatalogItemTable, { CollectionItemTable.contentId }, { CatalogItemTable.id })
                .select(CatalogItemTable.type, countColumn)
                .where {
                    (CollectionTable.userId eq userId) and
                    (CollectionTable.kind eq kind)
                }
                .groupBy(CatalogItemTable.type)
                .associate { it[CatalogItemTable.type] to it[countColumn].toInt() }

            return Triple(
                rows[CatalogItemType.CHART] ?: 0,
                rows[CatalogItemType.TOUR_PASS] ?: 0,
                rows[CatalogItemType.THEME] ?: 0
            )
        }

        // library: authored content (charts, tour passes, themes)
        val library: Triple<Int, Int, Int>? = if (all || "library" in requestedCounts) {
            // val charts = CatalogItemTable
            //                .innerJoin(ChartTable, { CatalogItemTable.id }, { ChartTable.id })
            //                .select(CatalogItemTable.id)
            //                .where { CatalogItemTable.authorId eq userId }
            //                .count()
            //                .toInt()
            //            val tourPasses = CatalogItemTable
            //                .innerJoin(TourPassTable, { CatalogItemTable.id }, { TourPassTable.id })
            //                .select(CatalogItemTable.id)
            //                .where { CatalogItemTable.authorId eq userId }
            //                .count()
            //                .toInt()
            //            val themes = CatalogItemTable
            //                .innerJoin(ThemeTable, { CatalogItemTable.id }, { ThemeTable.id })
            //                .select(CatalogItemTable.id)
            //                .where { CatalogItemTable.authorId eq userId }
            //                .count()
            //                .toInt()
            val charts = CatalogItemTable
                .innerJoin(ChartTable, { CatalogItemTable.id }, { ChartTable.id })
                .select(CatalogItemTable.id)
                .where { CatalogItemTable.authorId eq userId }
                .count()
                .toInt()
            val tourPasses = CatalogItemTable
                .innerJoin(TourPassTable, { CatalogItemTable.id }, { TourPassTable.id })
                .select(CatalogItemTable.id)
                .where { CatalogItemTable.authorId eq userId }
                .count()
                .toInt()
            val themes = CatalogItemTable
                .innerJoin(ThemeTable, { CatalogItemTable.id }, { ThemeTable.id })
                .select(CatalogItemTable.id)
                .where { CatalogItemTable.authorId eq userId }
                .count()
                .toInt()
             Triple(charts, tourPasses, themes)
        } else null

        val likes: Triple<Int, Int, Int>? = if (all || "likes" in requestedCounts) countByKind(CollectionKind.LIKES) else null
        val bookmarks: Triple<Int, Int, Int>? = if (all || "bookmarks" in requestedCounts) countByKind(CollectionKind.BOOKMARKS) else null

        val collections: Int? = if (all || "collections" in requestedCounts) {
            CollectionTable
                .select(CollectionTable.id)
                .where {
                    (CollectionTable.userId eq userId) and
                    (CollectionTable.kind eq CollectionKind.USER)
                }
                .count()
                .toInt()
        } else null

        val followers: Int? = if (all || "followers" in requestedCounts) followerCount else null
        val following: Int? = if (all || "following" in requestedCounts) followingCount else null

        UserProfileCounts(
            library = library,
            likes = likes,
            bookmarks = bookmarks,
            collections = collections,
            followers = followers,
            following = following
        )
    }

    override suspend fun getSystemCollectionItems(
        userId: UUID,
        collectionKind: CollectionKind,
        requestingUserId: UUID?,
        limit: Int
    ): List<CatalogItem> = suspendTransaction {
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
            ?: return@suspendTransaction emptyList()

        val collectionId = collection[CollectionTable.id].value

        // Use the collection repository to get items (respects visibility and proper fetching)
        collectionRepository.getCollectionItems(
            collectionId = collectionId,
            userId = requestingUserId,
            limit = limit,
        )
    }

    override suspend fun followUser(followerId: UUID, followedId: UUID): Boolean = suspendTransaction {
        // Cannot follow yourself
        if (followerId == followedId) return@suspendTransaction false

        // Verify both users exist in a single query
        val foundIds = UserTable
            .select(UserTable.id)
            .where { UserTable.id inList listOf(followerId, followedId) }
            .map { it[UserTable.id].value }
            .toSet()

        if (followerId !in foundIds) throw NotFoundException("Follower user not found")
        if (followedId !in foundIds) throw NotFoundException("User to follow not found")

        // insertIgnore: the composite PK (follower, followed) silently rejects duplicate rows,
        // eliminating a separate "already following" round-trip query.
        UserFollowTable.insertIgnore {
            it[follower] = followerId
            it[followed] = followedId
        }.insertedCount > 0
    }

    override suspend fun unfollowUser(followerId: UUID, followedId: UUID): Boolean = suspendTransaction {
        val deletedCount = UserFollowTable.deleteWhere {
            (UserFollowTable.follower eq followerId) and (UserFollowTable.followed eq followedId)
        }

        deletedCount > 0
    }

    override suspend fun getFollowers(userId: UUID, limit: Int, offset: Int): List<SimplifiedUser> = suspendTransaction {
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

    override suspend fun getFollowing(userId: UUID, limit: Int, offset: Int): List<SimplifiedUser> = suspendTransaction {
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

    override suspend fun isFollowing(followerId: UUID, followedId: UUID): Boolean = suspendTransaction {
        UserFollowTable.selectAll().where {
            (UserFollowTable.follower eq followerId) and (UserFollowTable.followed eq followedId)
        }.empty().not()
    }

    override suspend fun getUserUploads(
        userId: UUID,
        types: List<CatalogItemType>?,
        query: String?,
        sortBy: SortOption?,
        limit: Int,
        offset: Int
    ): Pair<List<CatalogItem>, Triple<Int, Int, Int>> = suspendTransaction {
        // Base query on CatalogItemTable
        val contentQuery = CatalogItemTable
            .leftJoin(ChartTable, { CatalogItemTable.id }, { ChartTable.id })
            .leftJoin(TrackTable, { ChartTable.trackId }, { TrackTable.id })
            .leftJoin(TourPassTable, { CatalogItemTable.id }, { TourPassTable.id })
            .leftJoin(ThemeTable, { CatalogItemTable.id }, { ThemeTable.id })
            .select(CatalogItemTable.id, CatalogItemTable.type)
            .where { CatalogItemTable.authorId eq userId }

        // Filter by types
        types?.let { requestedTypes ->
            contentQuery.andWhere { CatalogItemTable.type inList requestedTypes }
        }

        // Apply text search across all subtype tables
        query?.let { searchQuery ->
            contentQuery.andWhere {
                (TrackTable.artist like "%$searchQuery%") or
                (TrackTable.title like "%$searchQuery%") or
                (TourPassTable.name like "%$searchQuery%") or
                (TourPassTable.description like "%$searchQuery%") or
                (ThemeTable.name like "%$searchQuery%") or
                (ThemeTable.replaces like "%$searchQuery%")
            }
        }

        // Apply sorting
        when (sortBy) {
            SortOption.ALPHA_ASC -> contentQuery.orderBy(
                org.jetbrains.exposed.v1.core.coalesce(TrackTable.title, TourPassTable.name, ThemeTable.name) to SortOrder.ASC
            )
            SortOption.ALPHA_DESC -> contentQuery.orderBy(
                org.jetbrains.exposed.v1.core.coalesce(TrackTable.title, TourPassTable.name, ThemeTable.name) to SortOrder.DESC
            )
            SortOption.MOST_DOWNLOADED -> contentQuery.orderBy(CatalogItemTable.downloadsSum to SortOrder.DESC)
            SortOption.LAST_UPDATED, SortOption.WEEKLY_RANK, SortOption.MOST_LIKED, null ->
                contentQuery.orderBy(CatalogItemTable.updatedAt to SortOrder.DESC)
        }

        // Get counts before pagination
        val countQuery = CatalogItemTable
            .innerJoin(ChartTable, { CatalogItemTable.id }, { ChartTable.id })
            .select(CatalogItemTable.id)
            .where { CatalogItemTable.authorId eq userId }
        val chartCount = countQuery.count().toInt()

        val tpCountQuery = CatalogItemTable
            .innerJoin(TourPassTable, { CatalogItemTable.id }, { TourPassTable.id })
            .select(CatalogItemTable.id)
            .where { CatalogItemTable.authorId eq userId }
        val tourPassCount = tpCountQuery.count().toInt()

        val themeCountQuery = CatalogItemTable
            .innerJoin(ThemeTable, { CatalogItemTable.id }, { ThemeTable.id })
            .select(CatalogItemTable.id)
            .where { CatalogItemTable.authorId eq userId }
        val themeCount = themeCountQuery.count().toInt()

        // Apply pagination
        val paginatedQuery = contentQuery.limit(limit).offset(offset.toLong())
        val contentIds = paginatedQuery.map { it[CatalogItemTable.id].value }

        if (contentIds.isEmpty()) return@suspendTransaction Pair(emptyList(), Triple(chartCount, tourPassCount, themeCount))

        // Group IDs by type for bulk fetching
        val typeMap = paginatedQuery.associate { it[CatalogItemTable.id].value to it[CatalogItemTable.type] }

        val chartIds = contentIds.filter { typeMap[it] == CatalogItemType.CHART }
        val tourPassIds = contentIds.filter { typeMap[it] == CatalogItemType.TOUR_PASS }
        val themeIds = contentIds.filter { typeMap[it] == CatalogItemType.THEME }

        // Fetch each type in bulk
        val charts = if (chartIds.isNotEmpty()) {
            chartRepository.getCharts(
                filters = ChartRepository.ChartFilters(chartIds = chartIds)
            ).first
        } else emptyList()

        val tourPasses = if (tourPassIds.isNotEmpty()) {
            tourPassRepository.getTourPasses(contentIds = tourPassIds)
        } else emptyList()

        val themes = if (themeIds.isNotEmpty()) {
            themeRepository.getThemes(contentIds = themeIds)
        } else emptyList()

        // Merge all items and preserve original ordering
        val allItems = (charts + tourPasses + themes).associateBy { it.id }
        val orderedItems = contentIds.mapNotNull { allItems[it] }

        Pair(orderedItems, Triple(chartCount, tourPassCount, themeCount))
    }

    override suspend fun getLibraryCounts(userId: UUID): Triple<Int, Int, Int> = suspendTransaction {
        val charts = CatalogItemTable
            .innerJoin(ChartTable, { CatalogItemTable.id }, { ChartTable.id })
            .select(CatalogItemTable.id)
            .where { CatalogItemTable.authorId eq userId }
            .count()
            .toInt()
        val tourPasses = CatalogItemTable
            .innerJoin(TourPassTable, { CatalogItemTable.id }, { TourPassTable.id })
            .select(CatalogItemTable.id)
            .where { CatalogItemTable.authorId eq userId }
            .count()
            .toInt()
        val themes = CatalogItemTable
            .innerJoin(ThemeTable, { CatalogItemTable.id }, { ThemeTable.id })
            .select(CatalogItemTable.id)
            .where { CatalogItemTable.authorId eq userId }
            .count()
            .toInt()
        Triple(charts, tourPasses, themes)
    }
}


