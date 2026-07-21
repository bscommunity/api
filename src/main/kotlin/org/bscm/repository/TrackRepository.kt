package org.bscm.repository

import org.bscm.models.StreamingRef
import org.bscm.models.Track
import org.bscm.models.dao.AlbumEntity
import org.bscm.models.dao.TrackEntity
import org.bscm.models.dao.TrackStreamingRefEntity
import org.bscm.models.enums.Genre
import org.bscm.models.tables.TrackStreamingRefTable
import org.bscm.models.tables.TrackTable
import org.bscm.storage.StorageService
import org.bscm.utils.QueryUtils
import org.bscm.utils.StreamingPlatformUtils
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.batchInsert
import java.util.*

class TrackRepository(
    private val storageService: StorageService,
) {
    fun findOrCreate(
        title: String,
        artist: String,
        album: AlbumEntity?,
        isrc: String?,
        genres: List<Genre>,
        bpm: Int?,
        duration: Float,
    ): TrackEntity {
        isrc?.let { code ->
            TrackEntity.find { TrackTable.isrc eq code }
                .firstOrNull()
                ?.let { return it }
        }

        val normTitle = QueryUtils.getNormalizedQuery(title)
        val normArtist = QueryUtils.getNormalizedQuery(artist)

        val albumCondition = if (album != null) {
            TrackTable.albumId eq album.id
        } else {
            TrackTable.albumId.isNull()
        }

        TrackEntity.find {
            (TrackTable.normalizedTitle eq normTitle) and
            (TrackTable.normalizedArtist eq normArtist) and
            albumCondition
        }.firstOrNull()?.let { return it }

        return TrackEntity.new {
            this.title = title
            this.artist = artist
            this.album = album
            this.isrc = isrc
            this.genres = genres
            this.bpm = bpm
            this.duration = duration
            this.normalizedTitle = normTitle
            this.normalizedArtist = normArtist
        }
    }

    suspend fun attachStreamingRefs(
        trackId: UUID,
        refs: List<StreamingRef>
    ) {
        if (refs.isEmpty()) return

        val track = TrackEntity[trackId]

        val externalIds = refs.map { it.externalId }
        val existingIds = TrackStreamingRefEntity.find {
            TrackStreamingRefTable.externalId inList externalIds
        }.map { it.externalId }.toSet()

        val newRefs = refs
            .filter { it.externalId !in existingIds }
            .distinctBy { it.platform }

        if (newRefs.isEmpty()) return

        TrackStreamingRefTable.batchInsert(newRefs) { ref ->
            this[TrackStreamingRefTable.trackId] = track.id
            this[TrackStreamingRefTable.platform] = ref.platform
            this[TrackStreamingRefTable.externalId] = ref.externalId
        }
    }

    fun applyMetadataUpdates(
        track: TrackEntity,
        title: String?,
        artist: String?,
        album: AlbumEntity?,
        genres: List<Genre>?,
    ) {
        title?.let {
            track.title = it
            track.normalizedTitle = QueryUtils.getNormalizedQuery(it)
        }
        artist?.let {
            track.artist = it
            track.normalizedArtist = QueryUtils.getNormalizedQuery(it)
        }
        album?.let { track.album = it }
        genres?.let { track.genres = it }
    }

    fun toTrack(entity: TrackEntity, streamingRefs: List<StreamingRef>): Track = Track(
        id = entity.id.value,
        title = entity.title,
        artist = entity.artist,
        album = entity.album?.name,
        isrc = entity.isrc,
        genres = entity.genres.orEmpty(),
        bpm = entity.bpm,
        duration = entity.duration,
        streamingRefs = streamingRefs,
        coverUrl = entity.album?.let { storageService.albumCoverUrl(it.id.value) } ?: "",
        previewUrl = storageService.trackPreviewUrl(entity.id.value),
    )

    fun toStreamingRefs(entities: List<TrackStreamingRefEntity>): List<StreamingRef> =
        entities.map {
            StreamingRef(
                platform = it.platform,
                url = StreamingPlatformUtils.buildUrl(it.platform, it.externalId)
            )
        }
}
