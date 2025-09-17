package org.bscm.repository.implementation

import io.ktor.server.plugins.*
import org.bscm.models.Theme
import org.bscm.models.dao.ThemeEntity
import org.bscm.models.tables.ThemeTable
import org.bscm.repository.ThemeRepository
import org.bscm.utils.NanoIdUtils
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDateTime
import java.util.*

class ThemeRepositoryImpl : ThemeRepository {

    private fun daoToTheme(entity: ThemeEntity): Theme {
        return Theme(
            id = entity.id.value.toString(),
            shareId = entity.shareId,
            name = entity.name,
            replaces = entity.replaces,
            coverUrl = entity.coverUrl,
            previewUrl = entity.previewUrl,
            isPublic = entity.isPublic,
            isFeatured = entity.isFeatured,
            downloadsSum = entity.downloadsSum,
            latestPublishedAt = entity.latestPublishedAt ?: LocalDateTime.now()
        )
    }

    override suspend fun getThemes(
        userId: UUID?,
        themeIds: List<ULong>?,
        search: String?,
        limit: Int?,
        offset: Int?
    ): List<Theme> = newSuspendedTransaction {
        val query = ThemeTable.selectAll()

        if (!themeIds.isNullOrEmpty()) {
            query.andWhere { ThemeTable.id inList themeIds }
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
        themeEntities.map { daoToTheme(it) }
    }

    override suspend fun getThemeById(id: ULong): Theme? = newSuspendedTransaction {
        val entity = ThemeEntity.findById(id) ?: return@newSuspendedTransaction null
        daoToTheme(entity)
    }

    override suspend fun getAppThemeById(shareId: String): Theme? = newSuspendedTransaction {
        val entity = ThemeEntity.find { ThemeTable.shareId eq shareId }.firstOrNull()
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
        val entity = ThemeEntity.new {
            this.shareId = NanoIdUtils.generate()
            this.name = name
            this.replaces = replaces
            this.coverUrl = coverUrl
            this.previewUrl = previewUrl
            this.isPublic = true
            this.isFeatured = false
            this.downloadsSum = 0
            this.latestPublishedAt = LocalDateTime.now()
        }
        daoToTheme(entity)
    }

    override suspend fun updateTheme(
        id: ULong,
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

    override suspend fun deleteTheme(id: ULong): Boolean = newSuspendedTransaction {
        val entity = ThemeEntity.findById(id) ?: return@newSuspendedTransaction false
        entity.delete()
        true
    }
}
