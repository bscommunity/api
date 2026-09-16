package org.bscm.services

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.logging.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.bscm.models.dao.UserEntity
import org.bscm.models.tables.UserTable
import org.bscm.storage.StoragePaths
import org.bscm.storage.StorageService
import org.bscm.utils.MediaConverter
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.*

private val log = KtorSimpleLogger("AvatarService")

/**
 * Mirrors Discord avatars into our own storage, following the same
 * fetch → convert → upload pattern as track/album covers
 * ([MediaConverter.convertToAvif] + [StorageService]).
 *
 * The `users` table keeps an [UserTable.avatarKey] (storage object key,
 * resolved to a CDN URL via [StorageService.userAvatarUrl]) plus the
 * last-mirrored [UserTable.avatarHash] for change detection.
 *
 * Refresh triggers (no background job):
 * - Discord login ([syncUserAvatar], awaited): the fresh OAuth hash reveals
 *   avatar changes, so a changed hash re-mirrors immediately.
 * - Publish ([ensureMirrored], awaited before manifest generation): backfills
 *   users that have a Discord hash but no stored object yet (e.g. created
 *   before mirroring existed). Never blocks on already-mirrored users.
 */
class AvatarService(
    private val storageService: StorageService,
    private val client: HttpClient,
) {
    companion object {
        const val AVATAR_SIZE_PX = 256

        fun discordCdnUrl(discordId: String, avatarHash: String): String =
            "https://cdn.discordapp.com/avatars/$discordId/$avatarHash.png?size=$AVATAR_SIZE_PX"
    }

    /**
     * Mirrors the avatar for a user that just authenticated via Discord.
     * Re-fetches when [avatarHash] differs from the last-mirrored hash (or no
     * mirror exists yet); clears the mirror when the user has no avatar.
     * Returns false when the user is unknown or the mirror failed — callers
     * treat the avatar as absent in that case.
     */
    suspend fun syncUserAvatar(userId: UUID, discordId: String, avatarHash: String?): Boolean {
        val stored = suspendTransaction {
            UserEntity.findById(userId)?.let { Triple(it.discordId, it.avatarHash, it.avatarKey) }
        } ?: return false

        if (avatarHash == null) {
            if (stored.second != null || stored.third != null) {
                runCatching { storageService.deleteUserAvatar(userId) }
                suspendTransaction {
                    UserEntity[userId].apply {
                        this.avatarHash = null
                        this.avatarKey = null
                    }
                }
            }
            return true
        }

        if (avatarHash == stored.second && stored.third != null) return true

        val key = mirror(userId, discordId, avatarHash) ?: return false
        suspendTransaction {
            UserEntity[userId].apply {
                this.avatarHash = avatarHash
                this.avatarKey = key
            }
        }
        return true
    }

    /**
     * Backfills mirrors for [userIds] that have a Discord hash but no stored
     * object yet. Users without a hash, or already mirrored, cost a single
     * batched read and no downloads.
     */
    suspend fun ensureMirrored(userIds: Collection<UUID>) = coroutineScope {
        val distinct = userIds.distinct()
        if (distinct.isEmpty()) return@coroutineScope

        val missing = suspendTransaction {
            UserEntity.find { UserTable.id inList distinct }
                .filter { it.avatarHash != null && it.avatarKey == null }
                .map { Triple(it.id.value, it.discordId, it.avatarHash!!) }
        }

        missing.map { (id, discordId, hash) ->
            async {
                val key = mirror(id, discordId, hash) ?: return@async
                suspendTransaction {
                    UserEntity[id].apply {
                        this.avatarHash = hash
                        this.avatarKey = key
                    }
                }
            }
        }.awaitAll()
    }

    /**
     * Downloads the avatar from Discord's CDN, converts it to AVIF the same
     * way covers are processed (small avatar dimensions), and uploads it via
     * the storage adapter. Returns the storage key on success, null on failure.
     */
    private suspend fun mirror(userId: UUID, discordId: String, avatarHash: String): String? {
        return try {
            val response = client.get(discordCdnUrl(discordId, avatarHash))
            if (!response.status.isSuccess()) {
                log.warn("Failed to download avatar for user $userId: ${response.status}")
                return null
            }
            val rawBytes = response.readRawBytes()
            val avifBytes = MediaConverter.convertToAvif(rawBytes, AVATAR_SIZE_PX) ?: rawBytes
            storageService.uploadUserAvatar(userId, avifBytes)
            StoragePaths.userAvatar(userId).also {
                log.info("Mirrored avatar for user $userId: $it")
            }
        } catch (e: Exception) {
            log.warn("Failed to mirror avatar for user $userId: ${e.message}")
            null
        }
    }
}
