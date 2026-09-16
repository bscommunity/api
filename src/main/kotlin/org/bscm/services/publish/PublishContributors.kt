package org.bscm.services.publish

import org.bscm.models.dto.contributor.SimplifiedContributor
import org.bscm.models.enums.ContributorRole
import org.bscm.models.interfaces.IUserRepository
import org.bscm.utils.DecodingUtils
import java.util.*

/**
 * Resolves the `contributors` array embedded in bundle `bscm.json` metadata.
 *
 * Both chart and theme bundles carry the same initial contributors persisted to
 * the database, so offline clients (the Android app reads `bscm.json` and renders
 * "The following users contributed to this chart") credit everyone, not just the
 * author. The bundle carries one entry per user with a `roles` array of lowercase
 * enum names ("chart", "audio", ...) — the exact keys the Android
 * `ChartStorageScanner.roleIdsByName` map resolves.
 *
 * Unknown user IDs are skipped: the repository layer rejects them right after,
 * aborting the publish with Discord cleanup, so they must never reach the bundle.
 *
 * Users are resolved with a single batched [IUserRepository.getUsersByIds] lookup
 * instead of one query per contributor (N+1).
 */
suspend fun IUserRepository.resolveMetadataContributors(
    authorId: UUID,
    authorUsername: String,
    authorAvatarUrl: String?,
    contributors: List<SimplifiedContributor>,
): List<DecodingUtils.MetadataContributor> = buildList {
    // Flatten grouped input to one (userId, role) pair per role, then group by
    // user so the bundle carries one entry per user. Dedupe up front so the
    // batched lookup below covers each user once.
    val rolesByUser = contributors
        .flatMap { contributor -> contributor.roles.distinct().map { contributor.userId to it } }
        .filterNot { (userId, role) -> role == ContributorRole.AUTHOR && userId == authorId }
        .distinct()
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, roles) -> roles.sortedBy { it.id }.map { it.name.lowercase() } }
    if (rolesByUser.isEmpty()) {
        add(
            DecodingUtils.MetadataContributor(
                username = authorUsername,
                avatarUrl = authorAvatarUrl,
                roles = listOf("author"),
            )
        )
        return@buildList
    }

    val usersById = getUsersByIds(rolesByUser.keys.toList())

    // Author first, merging any extra roles they hold beyond AUTHOR.
    add(
        DecodingUtils.MetadataContributor(
            username = authorUsername,
            avatarUrl = authorAvatarUrl,
            roles = buildList {
                add("author")
                rolesByUser[authorId]?.let { addAll(it) }
            },
        )
    )

    rolesByUser.entries
        .filter { (userId, _) -> userId != authorId }
        .mapNotNull { (userId, roles) ->
            val contributorUser = usersById[userId]
                ?: return@mapNotNull null
            DecodingUtils.MetadataContributor(
                username = contributorUser.username,
                avatarUrl = contributorUser.avatarUrl,
                roles = roles,
            )
        }.forEach { add(it) }
}
