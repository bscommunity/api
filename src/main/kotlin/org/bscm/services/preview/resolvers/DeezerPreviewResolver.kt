package org.bscm.services.preview.resolvers

import kotlinx.datetime.Instant
import org.bscm.models.dto.PreviewResponse
import org.bscm.models.enums.PreviewProvider
import org.bscm.services.track.resolvers.DeezerClient

class DeezerPreviewResolver(
    private val deezerApi: DeezerClient
) : PreviewResolver {

    override suspend fun resolve(trackId: String): PreviewResponse? {
        val track = deezerApi.getTrack(trackId)
        val previewUrl = track?.preview ?: return null

        val expiresAt = extractExpiration(previewUrl)

        return PreviewResponse(
            url = previewUrl,
            provider = PreviewProvider.DEEZER,
            expiresAt = expiresAt
        )
    }

    private fun extractExpiration(url: String): Instant? {
        val regex = Regex("exp=(\\d+)")
        val match = regex.find(url) ?: return null
        return Instant.fromEpochSeconds(match.groupValues[1].toLong())
    }
}