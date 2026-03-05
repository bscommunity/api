package org.bscm.repository

import io.ktor.server.plugins.*
import org.bscm.models.Theme
import org.bscm.models.dao.ContentEntity
import org.bscm.models.dao.ThemeEntity
import org.bscm.models.enums.ContentType
import org.bscm.models.interfaces.IThemeRepository
import org.bscm.models.tables.ThemeTable
import org.bscm.utils.UserStatsUtils
import org.bscm.utils.retryOnConflict
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDateTime
import java.util.*

class ThemeRepository : BaseRepository(), IThemeRepository {

    private fun daoToTheme(
        entity: ThemeEntity,
        likedAt: LocalDateTime? = null,
        bookmarkedAt: LocalDateTime? = null
    ): Theme {
        return Theme(
            id = entity.id.value.toString(),
            contentId = ContentEntity[entity.contentId].id.value,
            name = entity.name,
            replaces = entity.replaces,
            coverUrl = entity.coverUrl,
            previewUrl = entity.previewUrl,
            isPublic = entity.isPublic,
            isFeatured = entity.isFeatured,
            likedAt = likedAt,
            bookmarkedAt = bookmarkedAt,
            downloadsSum = entity.downloadsSum,
            createdAt = entity.createdAt,
            updatedAt = entity.latestUpdatedAt ?: LocalDateTime.now()
        )
    }

    override suspend fun getThemes(
        userId: UUID?,
        contentIds: List<String>?,
        search: String?,
        limit: Int?,
        offset: Int?
    ): List<Theme> = newSuspendedTransaction {
        val query = ThemeTable.selectAll()

        if (!contentIds.isNullOrEmpty()) {
            query.andWhere { ThemeTable.contentId inList contentIds }
        }

        if (!search.isNullOrBlank()) {
            query.andWhere {
                (ThemeTable.name like "%$search%") or
                        (ThemeTable.replaces like "%$search%")
            }
        }

        if (limit != null) {
            query.limit(limit).offset(offset?.toLong() ?: 0)
        }

        val themeEntities = ThemeEntity.wrapRows(query).toList()

        // Fetch user stats for all themes in one query
        val themeContentIds = themeEntities.map { ContentEntity[it.contentId].id.value }
        val userStats = UserStatsUtils.fetchUserStats(getUserContext()?.userId, themeContentIds)

        themeEntities.map { entity ->
            val contentId = ContentEntity[entity.contentId].id.value
            val stats = userStats[contentId] ?: Pair(null, null)
            daoToTheme(entity, stats.first, stats.second)
        }
    }

    override suspend fun getThemeById(id: ULong): Theme? = newSuspendedTransaction {
        val entity = ThemeEntity.findById(id) ?: return@newSuspendedTransaction null
        daoToTheme(entity)
    }

    override suspend fun getAppThemeById(contentId: String): Theme? = newSuspendedTransaction {
        val entity = ThemeEntity.find { ThemeTable.contentId eq contentId }.firstOrNull()
            ?: return@newSuspendedTransaction null
        daoToTheme(entity)
    }

    override suspend fun createTheme(
        userId: UUID,
        name: String,
        replaces: String,
        coverUrl: String,
        previewUrl: String
    ): Theme = newSuspendedTransaction {
        // Generate a unique content entry
        val content = retryOnConflict {
            ContentEntity.new {
                this.type = ContentType.THEME
            }
        }

        val entity = ThemeEntity.new {
            this.contentId = content.id
            this.name = name
            this.replaces = replaces
            this.coverUrl = coverUrl
            this.previewUrl = previewUrl
            this.isPublic = true
            this.isFeatured = false
            this.downloadsSum = 0
            this.latestUpdatedAt = LocalDateTime.now()
        }
        daoToTheme(entity)
    }

    override suspend fun updateTheme(
        id: ULong,
        userId: UUID,
        name: String?,
        replaces: String?,
        coverUrl: String?,
        previewUrl: String?
    ): Theme = newSuspendedTransaction {
        val entity = ThemeEntity.findById(id) ?: throw NotFoundException("Theme not found")

        name?.let { entity.name = it }
        replaces?.let { entity.replaces = it }
        coverUrl?.let { entity.coverUrl = it }
        previewUrl?.let { entity.previewUrl = it }

        daoToTheme(entity)
    }

    override suspend fun deleteTheme(id: ULong, userId: UUID): Boolean = newSuspendedTransaction {
        val entity = ThemeEntity.findById(id) ?: return@newSuspendedTransaction false
        entity.delete()
        true
    }
}
