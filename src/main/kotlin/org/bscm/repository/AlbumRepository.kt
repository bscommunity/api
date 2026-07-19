package org.bscm.repository

import org.bscm.models.dao.AlbumEntity
import org.bscm.models.tables.AlbumTable
import org.bscm.utils.QueryUtils
import org.jetbrains.exposed.v1.core.eq

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
}
