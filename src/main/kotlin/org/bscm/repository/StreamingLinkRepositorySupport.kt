package org.bscm.repository

import org.bscm.models.StreamingLink
import org.bscm.models.dao.StreamingLinkEntity
import org.bscm.models.tables.StreamingLinkTable
import java.util.*

internal object StreamingLinkRepositorySupport {
    data class ResolvedLinks(
        val ids: List<UUID>,
        val entities: List<StreamingLinkEntity>,
    )

    /**
     * Resolves existing streaming links by URL and creates missing ones.
     * URLs are globally unique in the current schema, so URL is the stable lookup key.
     */
    fun resolveOrCreateLinks(links: List<StreamingLink>): ResolvedLinks {
        val deduped = links
            .map { StreamingLink(platform = it.platform, url = it.url.trim()) }
            .filter { it.url.isNotBlank() }
            .distinctBy { it.url }

        if (deduped.isEmpty()) return ResolvedLinks(emptyList(), emptyList())

        val urls = deduped.map { it.url }
        val existingByUrl = StreamingLinkEntity
            .find { StreamingLinkTable.url inList urls }
            .associateBy { it.url }

        val resolvedEntities = deduped.map { link ->
            existingByUrl[link.url] ?: StreamingLinkEntity.new {
                platform = link.platform
                url = link.url
            }
        }

        return ResolvedLinks(
            ids = resolvedEntities.map { it.id.value },
            entities = resolvedEntities,
        )
    }
}


