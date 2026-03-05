package org.bscm.services.preview.resolvers

import org.bscm.models.dto.PreviewResponse

interface PreviewResolver {
    suspend fun resolve(trackId: String): PreviewResponse?
}