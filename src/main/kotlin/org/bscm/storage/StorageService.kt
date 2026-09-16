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

    fun trackAudioPreviewUrl(trackId: UUID): String = publicUrl(StoragePaths.trackAudioPreview(trackId))

    suspend fun downloadTrackPreview(trackId: UUID): ByteArray =
        adapter.getObject(publicBucket, StoragePaths.trackAudioPreview(trackId))

    suspend fun deleteTrackPreview(trackId: UUID) {
        adapter.deleteObject(publicBucket, StoragePaths.trackAudioPreview(trackId))
    }

    suspend fun uploadTrackPreview(trackId: UUID, bytes: ByteArray) {
        adapter.putObject(
            bucket = publicBucket,
            path = StoragePaths.trackAudioPreview(trackId),
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

    // ── User avatars ────────────────────────────────────────────────────

    /** Resolves a stored avatar key (see `UserTable.avatarKey`) to its CDN URL. */
    fun userAvatarUrl(avatarKey: String): String = publicUrl(avatarKey)

    suspend fun uploadUserAvatar(userId: UUID, bytes: ByteArray) {
        adapter.putObject(
            bucket = publicBucket,
            path = StoragePaths.userAvatar(userId),
            bytes = bytes,
            contentType = StorageContentTypes.IMAGE_AVIF,
        )
    }

    suspend fun deleteUserAvatar(userId: UUID) {
        adapter.deleteObject(publicBucket, StoragePaths.userAvatar(userId))
    }
}
