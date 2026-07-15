package org.bscm.services

import io.ktor.util.logging.*
import org.bscm.models.tables.ChartTable
import org.bscm.models.tables.TrackTable
import org.bscm.storage.StorageService
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

        for (trackId in orphanedTrackIds) {
            runCatching { storageService.deleteTrackCover(trackId.value) }
                .onFailure { log.warn("Failed to delete cover for track ${trackId.value}: ${it.message}") }
            runCatching { storageService.deleteTrackPreview(trackId.value) }
                .onFailure { log.warn("Failed to delete preview for track ${trackId.value}: ${it.message}") }
        }

        val deleted = TrackTable.deleteWhere { TrackTable.id inList orphanedTrackIds }
        log.info("Deleted $deleted orphaned track(s)")
        deleted
    }
}
