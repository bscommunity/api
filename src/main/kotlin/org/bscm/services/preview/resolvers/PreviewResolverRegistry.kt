package org.bscm.services.preview.resolvers

import org.bscm.models.enums.PreviewProvider

class PreviewResolverRegistry(
    resolvers: List<PreviewResolver>
) {
    private val map = resolvers.associateBy {
        when (it) {
            is DeezerPreviewResolver -> PreviewProvider.DEEZER
            is ItunesPreviewResolver -> PreviewProvider.ITUNES
            else -> error("Unknown resolver")
        }
    }

    fun get(provider: PreviewProvider): PreviewResolver =
        map[provider] ?: error("No resolver for $provider")
}