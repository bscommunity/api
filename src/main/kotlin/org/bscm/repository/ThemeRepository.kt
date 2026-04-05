package org.bscm.repository

import io.ktor.server.plugins.*
import org.bscm.models.Theme
import org.bscm.models.dao.ContentEntity
import org.bscm.models.dao.ThemeEntity
import org.bscm.models.dao.UserEntity
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
            contentId = entity.content.id.value,
            name = entity.name,
            description = null,
            replaces = entity.replaces,
            coverUrl = entity.coverUrl,
            displayArtUrl = entity.displayArtUrl ?: entity.coverUrl,
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

        query.andWhere {
            if (userId == null) {
                ThemeTable.isPublic eq true
            } else {
                (ThemeTable.isPublic eq true) or (ThemeTable.authorId eq userId)
            }
        }

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
        val themeContentIds = themeEntities.map { it.content.id.value }
        val userStats = UserStatsUtils.fetchUserStats(getUserContext()?.userId, themeContentIds)

        themeEntities.map { entity ->
            val contentId = entity.content.id.value
            val stats = userStats[contentId] ?: Pair(null, null)
            daoToTheme(entity, stats.first, stats.second)
        }
    }

    override suspend fun getThemeById(id: ULong, userId: UUID?): Theme? = newSuspendedTransaction {
        val entity = ThemeEntity.findById(id) ?: return@newSuspendedTransaction null
        if (!entity.isPublic && entity.authorId.value != userId) {
            return@newSuspendedTransaction null
        }
        daoToTheme(entity)
    }

    override suspend fun getAppThemeById(contentId: String, userId: UUID?): Theme? = newSuspendedTransaction {
        val entity = ThemeEntity.find { ThemeTable.contentId eq contentId }.firstOrNull()
            ?: return@newSuspendedTransaction null
        if (!entity.isPublic && entity.authorId.value != userId) {
            return@newSuspendedTransaction null
        }
        daoToTheme(entity)
    }

    override suspend fun createTheme(
        userId: UUID,
        name: String,
        replaces: String,
        coverUrl: String,
        displayArtUrl: String,
        previewUrl: String
        ,
        id: ULong?
    ): Theme = newSuspendedTransaction {
        // Generate a unique content entry
        val content = retryOnConflict {
            ContentEntity.new {
                this.type = ContentType.THEME
            }
        }

        val entity = if (id != null) {
            ThemeEntity.new(id) {
                this.contentId = content.id
                this.authorId = UserEntity[userId].id
                this.name = name
                this.replaces = replaces
                this.coverUrl = coverUrl
                this.displayArtUrl = displayArtUrl
                this.previewUrl = previewUrl
                this.isPublic = true
                this.isFeatured = false
                this.downloadsSum = 0
                this.latestUpdatedAt = LocalDateTime.now()
            }
        } else {
            ThemeEntity.new {
                this.contentId = content.id
                this.authorId = UserEntity[userId].id
                this.name = name
                this.replaces = replaces
                this.coverUrl = coverUrl
                this.displayArtUrl = displayArtUrl
                this.previewUrl = previewUrl
                this.isPublic = true
                this.isFeatured = false
                this.downloadsSum = 0
                this.latestUpdatedAt = LocalDateTime.now()
            }
        }
        daoToTheme(entity)
    }

    override suspend fun updateTheme(
        id: ULong,
        userId: UUID,
        name: String?,
        replaces: String?,
        coverUrl: String?,
        displayArtUrl: String?,
        previewUrl: String?
    ): Theme = newSuspendedTransaction {
        val entity = ThemeEntity.findById(id)
            ?.takeIf { it.authorId.value == userId }
            ?: throw NotFoundException("Theme not found")

        name?.let { entity.name = it }
        replaces?.let { entity.replaces = it }
        coverUrl?.let { entity.coverUrl = it }
        displayArtUrl?.let { entity.displayArtUrl = it }
        previewUrl?.let { entity.previewUrl = it }

        daoToTheme(entity)
    }

    override suspend fun deleteTheme(id: ULong, userId: UUID): Boolean = newSuspendedTransaction {
        val entity = ThemeEntity.findById(id)
            ?.takeIf { it.authorId.value == userId }
            ?: return@newSuspendedTransaction false
        entity.delete()
        true
    }
}
