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
 * author. Role names are lowercase enum names ("chart", "audio", ...) — the exact
 * keys the Android `ChartStorageScanner.roleIdsByName` map resolves.
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
    add(
        DecodingUtils.MetadataContributor(
            username = authorUsername,
            avatarUrl = authorAvatarUrl,
            role = "author",
        )
    )
    // Dedupe up front so the batched lookup below covers each user once.
    // Equivalent to distinct() on the mapped output, since the mapping is deterministic.
    val extras = contributors
        .filterNot { it.role == ContributorRole.AUTHOR && it.userId == authorId }
        .distinctBy { it.userId to it.role }
    if (extras.isEmpty()) return@buildList

    val usersById = getUsersByIds(extras.map { it.userId })
    extras.mapNotNull { contributor ->
        val contributorUser = usersById[contributor.userId]
            ?: return@mapNotNull null
        DecodingUtils.MetadataContributor(
            username = contributorUser.username,
            avatarUrl = contributorUser.avatarUrl,
            role = contributor.role.name.lowercase(),
        )
    }.forEach { add(it) }
}
