package org.bscm.services

import io.ktor.util.logging.*
import org.bscm.models.tables.AlbumTable
import org.bscm.models.tables.ChartTable
import org.bscm.models.tables.TrackTable
import org.bscm.storage.StorageService
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

private val log = KtorSimpleLogger("TrackCleanupService")

class TrackCleanupService(
    private val storageService: StorageService,
) {
    suspend fun cleanupOrphanedTracks(): Int = suspendTransaction {
        val referencedTrackIds = ChartTable
            .select(ChartTable.trackId)
            .distinct()
            .map { it[ChartTable.trackId].value }
            .toSet()

        val orphanedTrackIds = TrackTable
            .select(TrackTable.id)
            .map { it[TrackTable.id] }
            .filter { it.value !in referencedTrackIds }

        if (orphanedTrackIds.isEmpty()) {
            log.info("No orphaned tracks found")
            return@suspendTransaction 0
        }

        log.info("Found ${orphanedTrackIds.size} orphaned track(s), cleaning up...")

        // Collect album IDs from orphaned tracks before deleting them
        val orphanedAlbumIds = TrackTable
            .select(TrackTable.albumId)
            .where { TrackTable.id inList orphanedTrackIds }
            .mapNotNull { it[TrackTable.albumId]?.value }
            .toSet()

        for (trackId in orphanedTrackIds) {
            runCatching { storageService.deleteTrackPreview(trackId.value) }
                .onFailure { log.warn("Failed to delete preview for track ${trackId.value}: ${it.message}") }
        }

        val deleted = TrackTable.deleteWhere { TrackTable.id inList orphanedTrackIds }
        log.info("Deleted $deleted orphaned track(s)")

        // Clean up albums that are no longer referenced by any track
        for (albumId in orphanedAlbumIds) {
            val stillReferenced = TrackTable
                .select(TrackTable.id)
                .where { TrackTable.albumId eq albumId }
                .firstOrNull() != null

            if (!stillReferenced) {
                log.info("Album $albumId has no remaining tracks, cleaning up...")
                runCatching { storageService.deleteAlbumCover(albumId) }
                    .onFailure { log.warn("Failed to delete album cover for album $albumId: ${it.message}") }
                AlbumTable.deleteWhere { AlbumTable.id eq albumId }
            }
        }

        deleted
    }
}
