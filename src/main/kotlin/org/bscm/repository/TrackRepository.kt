package org.bscm.repository

import org.bscm.models.StreamingRef
import org.bscm.models.Track
import org.bscm.models.dao.TrackEntity
import org.bscm.models.dao.TrackStreamingRefEntity
import org.bscm.models.enums.Genre
import org.bscm.models.tables.TrackStreamingRefTable
import org.bscm.models.tables.TrackTable
import org.bscm.storage.StorageService
import org.bscm.utils.QueryUtils
import org.bscm.utils.StreamingPlatformUtils
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.*

class TrackRepository(
    private val storageService: StorageService,
) {
    suspend fun findOrCreate(
        title: String,
        artist: String,
        album: String?,
        isrc: String?,
        genre: Genre?,
        bpm: Int?,
        duration: Float,
    ): TrackEntity = newSuspendedTransaction {
        isrc?.let { code ->
            TrackEntity.find { TrackTable.isrc eq code }
                .firstOrNull()
                ?.let { return@newSuspendedTransaction it }
        }

        TrackEntity.new {
            this.title = title
            this.artist = artist
            this.album = album
            this.isrc = isrc
            this.genre = genre
            this.bpm = bpm
            this.duration = duration
            this.normalizedTitle = QueryUtils.getNormalizedQuery(title)
            this.normalizedArtist = QueryUtils.getNormalizedQuery(artist)
            this.normalizedAlbum = album?.let(QueryUtils::getNormalizedQuery)
        }
    }

    suspend fun attachStreamingRefs(
        trackId: UUID,
        refs: List<StreamingRef>
    ) = newSuspendedTransaction {
        if (refs.isEmpty()) return@newSuspendedTransaction

        val track = TrackEntity[trackId]

        refs.forEach { ref ->
            val existing = TrackStreamingRefEntity.find {
                (TrackStreamingRefTable.trackId eq track.id) and
                        (TrackStreamingRefTable.platform eq ref.platform)
            }.firstOrNull()

            if (existing == null) {
                TrackStreamingRefEntity.new {
                    this.track = track
                    this.platform = ref.platform
                    this.externalId = ref.externalId
                }
            }
        }
    }

    fun applyMetadataUpdates(
        track: TrackEntity,
        title: String?,
        artist: String?,
        album: String?,
        genre: Genre?,
    ) {
        title?.let {
            track.title = it
            track.normalizedTitle = QueryUtils.getNormalizedQuery(it)
        }
        artist?.let {
            track.artist = it
            track.normalizedArtist = QueryUtils.getNormalizedQuery(it)
        }
        album?.let {
            track.album = it
            track.normalizedAlbum = QueryUtils.getNormalizedQuery(it)
        }
        genre?.let { track.genre = it }
    }

    fun toTrack(entity: TrackEntity, streamingRefs: List<StreamingRef>): Track = Track(
        id = entity.id.value,
        title = entity.title,
        artist = entity.artist,
        album = entity.album,
        isrc = entity.isrc,
        genre = entity.genre,
        bpm = entity.bpm,
        duration = entity.duration,
        streamingRefs = streamingRefs,
        coverUrl = storageService.trackCoverUrl(entity.id.value),
        previewUrl = storageService.trackPreviewUrl(entity.id.value),
    )

    fun toStreamingRefs(entities: List<TrackStreamingRefEntity>): List<StreamingRef> =
        entities.map {
            StreamingRef(
                platform = it.platform,
                url = StreamingPlatformUtils.buildUrl(it.platform, it.externalId)
            )
        }

    fun toStreamingRefs(rows: List<Pair<Int, String>>): List<StreamingRef> = rows.mapNotNull { (platformId, externalId) ->
        val platform = org.bscm.models.enums.StreamingPlatform.entries.firstOrNull { it.id == platformId }
            ?: return@mapNotNull null
        StreamingRef(platform = platform, url = StreamingPlatformUtils.buildUrl(platform, externalId))
    }
}
