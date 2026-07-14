package org.bscm.storage

import java.util.*

class StorageService(
    private val adapter: StorageAdapter,
    private val publicBucket: String,
) {
    fun publicUrl(path: String): String = adapter.publicUrl(publicBucket, path)

    // ── Tracks ──────────────────────────────────────────────────────────

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

    // ── Generic asset covers (charts, themes, tour passes) ──────────────

    fun assetCoverUrl(key: String): String = publicUrl(StoragePaths.assetCover(key))

    suspend fun uploadAssetCover(key: String, bytes: ByteArray) {
        adapter.putObject(
            bucket = publicBucket,
            path = StoragePaths.assetCover(key),
            bytes = bytes,
            contentType = StorageContentTypes.IMAGE_PNG,
        )
    }

    suspend fun deleteAssetCover(key: String) {
        adapter.deleteObject(publicBucket, StoragePaths.assetCover(key))
    }

    // ── Themes (display art) ────────────────────────────────────────────

    fun themeDisplayUrl(themeId: String): String = publicUrl(StoragePaths.themeDisplay(themeId))

    suspend fun uploadThemeDisplay(themeId: String, bytes: ByteArray) {
        adapter.putObject(
            bucket = publicBucket,
            path = StoragePaths.themeDisplay(themeId),
            bytes = bytes,
            contentType = StorageContentTypes.IMAGE_PNG,
        )
    }

    suspend fun deleteThemeDisplay(themeId: String) {
        adapter.deleteObject(publicBucket, StoragePaths.themeDisplay(themeId))
    }
}
