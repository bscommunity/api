package org.bscm.repository

import org.bscm.models.StreamingRef
import org.bscm.models.dao.AlbumEntity
import org.bscm.models.dao.AlbumStreamingRefEntity
import org.bscm.models.tables.AlbumStreamingRefTable
import org.bscm.models.tables.AlbumTable
import org.bscm.utils.QueryUtils
import org.bscm.utils.StreamingPlatformUtils
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.*

class AlbumRepository {
    suspend fun findOrCreate(name: String): AlbumEntity = suspendTransaction {
        val normalizedName = QueryUtils.getNormalizedQuery(name)

        AlbumEntity.find { AlbumTable.normalizedName eq normalizedName }
            .firstOrNull()?.let { existing ->
                return@suspendTransaction existing
            }

        AlbumEntity.new {
            this.name = name
            this.normalizedName = normalizedName
        }
    }

    suspend fun attachStreamingRefs(
        albumId: UUID,
        refs: List<StreamingRef>
    ) = suspendTransaction {
        if (refs.isEmpty()) return@suspendTransaction

        val externalIds = refs.map { it.externalId }
        val existingIds = AlbumStreamingRefEntity.find {
            AlbumStreamingRefTable.externalId inList externalIds
        }.map { it.externalId }.toSet()

        val newRefs = refs
            .filter { it.externalId !in existingIds }
            .distinctBy { it.platform }

        if (newRefs.isEmpty()) return@suspendTransaction

        AlbumStreamingRefTable.batchInsert(newRefs, ignore = true) { ref ->
            this[AlbumStreamingRefTable.albumId] = albumId
            this[AlbumStreamingRefTable.platform] = ref.platform
            this[AlbumStreamingRefTable.externalId] = ref.externalId
        }
    }

    suspend fun getStreamingRefs(albumId: UUID): List<StreamingRef> = suspendTransaction {
        AlbumStreamingRefEntity.find { AlbumStreamingRefTable.albumId eq albumId }
            .map { StreamingRef(it.platform, StreamingPlatformUtils.buildUrl(it.platform, it.externalId)) }
    }

    suspend fun getStreamingRefs(albumIds: List<UUID>): Map<UUID, List<StreamingRef>> = suspendTransaction {
        if (albumIds.isEmpty()) return@suspendTransaction emptyMap()

        val entityIds = albumIds.map { EntityID(it, AlbumTable) }
        AlbumStreamingRefTable.selectAll()
            .where { AlbumStreamingRefTable.albumId inList entityIds }
            .toList()
            .groupBy { it[AlbumStreamingRefTable.albumId].value }
            .mapValues { (_, rows) ->
                rows.map {
                    StreamingRef(
                        platform = it[AlbumStreamingRefTable.platform],
                        url = StreamingPlatformUtils.buildUrl(it[AlbumStreamingRefTable.platform], it[AlbumStreamingRefTable.externalId]),
                    )
                }
            }
    }
}
