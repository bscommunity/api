package org.bscm.repository.implementation

import org.bscm.models.Collection
import org.bscm.models.CollectionItem
import org.bscm.models.dao.*
import org.bscm.models.enums.ContentType
import org.bscm.models.tables.CollectionItemTable
import org.bscm.models.tables.CollectionTable
import org.bscm.repository.UserCollectionRepository
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDateTime
import java.util.*

class UserCollectionRepositoryImpl : UserCollectionRepository {

    override suspend fun createCollection(userId: UUID, name: String, isPublic: Boolean): Collection =
        newSuspendedTransaction {
            val now = LocalDateTime.now()
            val entity = CollectionEntity.new {
                user = UserEntity[userId]
                this.name = name
                this.isPublic = isPublic
                createdAt = now
                updatedAt = now
            }

            Collection(
                id = entity.id.value,
                userId = userId,
                name = entity.name,
                isPublic = entity.isPublic,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                itemCount = 0
            )
        }

    override suspend fun getUserCollections(userId: UUID): List<Collection> = newSuspendedTransaction {
        CollectionEntity.find { CollectionTable.userId eq userId }
            .orderBy(CollectionTable.updatedAt to SortOrder.DESC)
            .map { entity ->
                val itemCount = CollectionItemTable.selectAll()
                    .where { CollectionItemTable.collectionId eq entity.id }
                    .count().toInt()

                Collection(
                    id = entity.id.value,
                    userId = entity.user.id.value,
                    name = entity.name,
                    isPublic = entity.isPublic,
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt,
                    itemCount = itemCount
                )
            }
    }

    override suspend fun getPublicCollections(limit: Int?, offset: Int?): List<Collection> = newSuspendedTransaction {
        val query = CollectionTable.selectAll()
            .where { CollectionTable.isPublic eq true }
            .orderBy(CollectionTable.updatedAt to SortOrder.DESC)

        if (limit != null) {
            query.limit(limit).offset(offset?.toLong() ?: 0)
        }

        query.map { row ->
            val itemCount = CollectionItemTable.selectAll()
                .where { CollectionItemTable.collectionId eq row[CollectionTable.id] }
                .count().toInt()

            Collection(
                id = row[CollectionTable.id].value,
                userId = row[CollectionTable.userId].value,
                name = row[CollectionTable.name],
                isPublic = row[CollectionTable.isPublic],
                createdAt = row[CollectionTable.createdAt],
                updatedAt = row[CollectionTable.updatedAt],
                itemCount = itemCount
            )
        }
    }

    override suspend fun getCollection(collectionId: ULong, userId: UUID?): Collection? = newSuspendedTransaction {
        val filter = if (userId != null) {
            (CollectionTable.id eq collectionId) and
                    ((CollectionTable.userId eq userId) or (CollectionTable.isPublic eq true))
        } else {
            (CollectionTable.id eq collectionId) and (CollectionTable.isPublic eq true)
        }

        CollectionTable.selectAll().where { filter }.firstOrNull()?.let { row ->
            val itemCount = CollectionItemTable.selectAll()
                .where { CollectionItemTable.collectionId eq collectionId }
                .count().toInt()

            Collection(
                id = row[CollectionTable.id].value,
                userId = row[CollectionTable.userId].value,
                name = row[CollectionTable.name],
                isPublic = row[CollectionTable.isPublic],
                createdAt = row[CollectionTable.createdAt],
                updatedAt = row[CollectionTable.updatedAt],
                itemCount = itemCount
            )
        }
    }

    override suspend fun updateCollection(collectionId: ULong, userId: UUID, name: String?, isPublic: Boolean?): Boolean =
        newSuspendedTransaction {
            val entity = CollectionEntity.find {
                (CollectionTable.id eq collectionId) and (CollectionTable.userId eq userId)
            }.firstOrNull() ?: return@newSuspendedTransaction false

            name?.let { entity.name = it }
            isPublic?.let { entity.isPublic = it }
            entity.updatedAt = LocalDateTime.now()
            true
        }

    override suspend fun deleteCollection(collectionId: ULong, userId: UUID): Boolean = newSuspendedTransaction {
        val entity = CollectionEntity.find {
            (CollectionTable.id eq collectionId) and (CollectionTable.userId eq userId)
        }.firstOrNull() ?: return@newSuspendedTransaction false

        entity.delete()
        true
    }

    override suspend fun addItemToCollection(collectionId: ULong, userId: UUID, contentType: ContentType, contentId: ULong): Boolean =
        newSuspendedTransaction {
            // Verificar se a coleção existe e pertence ao usuário
            val collection = CollectionEntity.find {
                (CollectionTable.id eq collectionId) and (CollectionTable.userId eq userId)
            }.firstOrNull() ?: return@newSuspendedTransaction false

            // Verificar se o item já existe na coleção
            val existingFilter = (CollectionItemTable.collectionId eq collectionId) and
                    getContentIdFilter(contentType, contentId)

            val existing = CollectionItemTable.selectAll().where { existingFilter }.firstOrNull()
            if (existing != null) return@newSuspendedTransaction false

            // Adicionar o item
            CollectionItemEntity.new {
                this.collection = collection
                when (contentType) {
                    ContentType.CHART -> this.chart = ChartEntity[contentId]
                    ContentType.TOUR_PASS -> this.tourPass = TourPassEntity[contentId]
                    ContentType.THEME -> this.theme = ThemeEntity[contentId]
                }
                addedAt = LocalDateTime.now()
            }

            // Atualizar timestamp da coleção
            collection.updatedAt = LocalDateTime.now()
            true
        }

    override suspend fun removeItemFromCollection(collectionId: ULong, userId: UUID, contentType: ContentType, contentId: ULong): Boolean =
        newSuspendedTransaction {
            // Verificar se a coleção pertence ao usuário
            val collection = CollectionEntity.find {
                (CollectionTable.id eq collectionId) and (CollectionTable.userId eq userId)
            }.firstOrNull() ?: return@newSuspendedTransaction false

            val filter = (CollectionItemTable.collectionId eq collectionId) and
                    getContentIdFilter(contentType, contentId)

            val item = CollectionItemEntity.find { filter }.firstOrNull()
                ?: return@newSuspendedTransaction false

            item.delete()
            collection.updatedAt = LocalDateTime.now()
            true
        }

    override suspend fun getCollectionItems(collectionId: ULong, userId: UUID?): List<CollectionItem> = newSuspendedTransaction {
        // Verificar acesso à coleção
        val hasAccess = if (userId != null) {
            CollectionTable.selectAll().where {
                (CollectionTable.id eq collectionId) and
                        ((CollectionTable.userId eq userId) or (CollectionTable.isPublic eq true))
            }.count() > 0
        } else {
            CollectionTable.selectAll().where {
                (CollectionTable.id eq collectionId) and (CollectionTable.isPublic eq true)
            }.count() > 0
        }

        if (!hasAccess) return@newSuspendedTransaction emptyList()

        CollectionItemTable.selectAll()
            .where { CollectionItemTable.collectionId eq collectionId }
            .orderBy(CollectionItemTable.addedAt to SortOrder.DESC)
            .map { row ->
                val (contentType, contentId) = getContentTypeAndIdFromRow(row)
                CollectionItem(
                    id = row[CollectionItemTable.id].value,
                    collectionId = collectionId,
                    contentType = contentType,
                    contentId = contentId,
                    addedAt = row[CollectionItemTable.addedAt]
                )
            }
    }

    override suspend fun isItemInCollection(collectionId: ULong, contentType: ContentType, contentId: ULong): Boolean =
        newSuspendedTransaction {
            val filter = (CollectionItemTable.collectionId eq collectionId) and
                    getContentIdFilter(contentType, contentId)

            CollectionItemTable.selectAll().where { filter }.count() > 0
        }

    override suspend fun getUserCollectionsContaining(userId: UUID, contentType: ContentType, contentId: ULong): List<Collection> =
        newSuspendedTransaction {
            val contentFilter = getContentIdFilter(contentType, contentId)

            (CollectionTable innerJoin CollectionItemTable)
                .selectAll()
                .where {
                    (CollectionTable.userId eq userId) and contentFilter
                }
                .map { row ->
                    Collection(
                        id = row[CollectionTable.id].value,
                        userId = row[CollectionTable.userId].value,
                        name = row[CollectionTable.name],
                        isPublic = row[CollectionTable.isPublic],
                        createdAt = row[CollectionTable.createdAt],
                        updatedAt = row[CollectionTable.updatedAt],
                        itemCount = 0 // We don't calculate here for performance
                    )
                }
        }

    override suspend fun getContentStats(collectionName: String, contentType: ContentType, contentId: ULong): Map<String, Int> =
        newSuspendedTransaction {
            val contentFilter = getContentIdFilter(contentType, contentId)

            // Count how many users have this content in collections with the specified name
            val count = (CollectionTable innerJoin CollectionItemTable)
                .selectAll()
                .where {
                    (CollectionTable.name eq collectionName) and contentFilter
                }
                .count().toInt()

            when (collectionName) {
                "Likes" -> mapOf("likes" to count)
                "Favorites" -> mapOf("favorites" to count)
                else -> mapOf("count" to count)
            }
        }

    private fun getContentIdFilter(contentType: ContentType, contentId: ULong): Op<Boolean> {
        return when (contentType) {
            ContentType.CHART -> CollectionItemTable.chartId eq contentId
            ContentType.TOUR_PASS -> CollectionItemTable.tourPassId eq contentId
            ContentType.THEME -> CollectionItemTable.themeId eq contentId
        }
    }

    private fun getContentTypeAndIdFromRow(row: ResultRow): Pair<ContentType, ULong> {
        return when {
            row[CollectionItemTable.chartId] != null ->
                ContentType.CHART to row[CollectionItemTable.chartId]!!.value
            row[CollectionItemTable.tourPassId] != null ->
                ContentType.TOUR_PASS to row[CollectionItemTable.tourPassId]!!.value
            row[CollectionItemTable.themeId] != null ->
                ContentType.THEME to row[CollectionItemTable.themeId]!!.value
            else -> throw IllegalStateException("CollectionItem must have at least one content reference")
        }
    }
}