package org.bscm.repository

import org.bscm.models.StreamingRef
import org.bscm.models.dao.AlbumEntity
import org.bscm.models.dao.AlbumStreamingRefEntity
import org.bscm.models.tables.AlbumStreamingRefTable
import org.bscm.models.tables.AlbumTable
import org.bscm.utils.QueryUtils
import org.bscm.utils.StreamingPlatformUtils
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.batchInsert
import java.util.*

class AlbumRepository {
    fun findOrCreate(name: String, coverUrl: String?): AlbumEntity {
        val normalizedName = QueryUtils.getNormalizedQuery(name)

        AlbumEntity.find { AlbumTable.normalizedName eq normalizedName }
            .firstOrNull()?.let { existing ->
                if (!coverUrl.isNullOrBlank() && existing.coverUrl.isNullOrBlank()) {
                    existing.coverUrl = coverUrl
                }
                return existing
            }

        return AlbumEntity.new {
            this.name = name
            this.normalizedName = normalizedName
            this.coverUrl = coverUrl
        }
    }

    suspend fun attachStreamingRefs(
        albumId: UUID,
        refs: List<StreamingRef>
    ) {
        if (refs.isEmpty()) return

        val externalIds = refs.map { it.externalId }
        val existingIds = AlbumStreamingRefEntity.find {
            AlbumStreamingRefTable.externalId inList externalIds
        }.map { it.externalId }.toSet()

        val newRefs = refs
            .filter { it.externalId !in existingIds }
            .distinctBy { it.platform }

        if (newRefs.isEmpty()) return

        AlbumStreamingRefTable.batchInsert(newRefs) { ref ->
            this[AlbumStreamingRefTable.albumId] = albumId
            this[AlbumStreamingRefTable.platform] = ref.platform
            this[AlbumStreamingRefTable.externalId] = ref.externalId
        }
    }

    fun getStreamingRefs(albumId: UUID): List<StreamingRef> {
        return AlbumStreamingRefEntity.find { AlbumStreamingRefTable.albumId eq albumId }
            .map { StreamingRef(it.platform, StreamingPlatformUtils.buildUrl(it.platform, it.externalId)) }
    }
}
