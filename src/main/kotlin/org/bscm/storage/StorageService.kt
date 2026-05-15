package org.bscm.storage

import java.util.*

class StorageService(
    private val adapter: StorageAdapter,
    private val publicBucket: String,
) {
    fun publicUrl(path: String): String = adapter.publicUrl(publicBucket, path)

    fun trackCoverUrl(trackId: UUID): String = publicUrl(StoragePaths.trackCover(trackId))

    fun trackPreviewUrl(trackId: UUID): String = publicUrl(StoragePaths.trackPreview(trackId))

    suspend fun downloadTrackCover(trackId: UUID): ByteArray =
        adapter.getObject(publicBucket, StoragePaths.trackCover(trackId))

    suspend fun downloadTrackPreview(trackId: UUID): ByteArray =
        adapter.getObject(publicBucket, StoragePaths.trackPreview(trackId))

    suspend fun deleteTrackCover(trackId: UUID) {
        adapter.deleteObject(publicBucket, StoragePaths.trackCover(trackId))
    }

    suspend fun deleteTrackPreview(trackId: UUID) {
        adapter.deleteObject(publicBucket, StoragePaths.trackPreview(trackId))
    }

    suspend fun uploadTrackCover(trackId: UUID, bytes: ByteArray) {
        adapter.putObject(
            bucket = publicBucket,
            path = StoragePaths.trackCover(trackId),
            bytes = bytes,
            contentType = StorageContentTypes.IMAGE_AVIF,
        )
    }

    suspend fun uploadTrackPreview(trackId: UUID, bytes: ByteArray) {
        adapter.putObject(
            bucket = publicBucket,
            path = StoragePaths.trackPreview(trackId),
            bytes = bytes,
            contentType = StorageContentTypes.AUDIO_OPUS,
        )
    }
}
