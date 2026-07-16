package org.bscm.services.preview.resolvers

import org.bscm.models.dto.PreviewResponse
import org.bscm.models.enums.PreviewProvider
import org.bscm.services.track.resolvers.ItunesClient

class ItunesPreviewResolver(
    private val itunesApi: ItunesClient
) : PreviewResolver {

    override suspend fun resolve(trackId: String): PreviewResponse? {
        val track = itunesApi.getTrack(trackId)
        val previewUrl = track?.previewUrl ?: return null

        return PreviewResponse(
            url = previewUrl,
            provider = PreviewProvider.ITUNES,
            expiresAt = null // URL estável
        )
    }
}