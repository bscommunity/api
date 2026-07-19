package org.bscm.storage

import java.util.*

class StorageService(
    private val adapter: StorageAdapter,
    private val publicBucket: String,
) {
    fun publicUrl(path: String): String = adapter.publicUrl(publicBucket, path)

    // ── Albums ──────────────────────────────────────────────────────────

    fun albumCoverUrl(albumId: UUID): String = publicUrl(StoragePaths.albumCover(albumId))

    suspend fun uploadAlbumCover(albumId: UUID, bytes: ByteArray) {
        adapter.putObject(
            bucket = publicBucket,
            path = StoragePaths.albumCover(albumId),
            bytes = bytes,
            contentType = StorageContentTypes.IMAGE_AVIF,
        )
    }

    suspend fun deleteAlbumCover(albumId: UUID) {
        adapter.deleteObject(publicBucket, StoragePaths.albumCover(albumId))
    }

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

    // ── Themes ──────────────────────────────────────────────────────────

    fun themeCoverUrl(themeId: String): String = publicUrl(StoragePaths.themeCover(themeId))

    fun themeDisplayUrl(themeId: String): String = publicUrl(StoragePaths.themeDisplay(themeId))

    suspend fun uploadThemeCover(themeId: String, bytes: ByteArray) {
        adapter.putObject(
            bucket = publicBucket,
            path = StoragePaths.themeCover(themeId),
            bytes = bytes,
            contentType = StorageContentTypes.IMAGE_AVIF,
        )
    }

    suspend fun uploadThemeDisplay(themeId: String, bytes: ByteArray) {
        adapter.putObject(
            bucket = publicBucket,
            path = StoragePaths.themeDisplay(themeId),
            bytes = bytes,
            contentType = StorageContentTypes.IMAGE_AVIF,
        )
    }

    suspend fun deleteThemeCover(themeId: String) {
        adapter.deleteObject(publicBucket, StoragePaths.themeCover(themeId))
    }

    suspend fun deleteThemeDisplay(themeId: String) {
        adapter.deleteObject(publicBucket, StoragePaths.themeDisplay(themeId))
    }

    // ── Tour Passes ─────────────────────────────────────────────────────

    fun tourPassCoverUrl(tourPassId: String): String = publicUrl(StoragePaths.tourPassCover(tourPassId))

    suspend fun uploadTourPassCover(tourPassId: String, bytes: ByteArray) {
        adapter.putObject(
            bucket = publicBucket,
            path = StoragePaths.tourPassCover(tourPassId),
            bytes = bytes,
            contentType = StorageContentTypes.IMAGE_AVIF,
        )
    }

    suspend fun deleteTourPassCover(tourPassId: String) {
        adapter.deleteObject(publicBucket, StoragePaths.tourPassCover(tourPassId))
    }
}
