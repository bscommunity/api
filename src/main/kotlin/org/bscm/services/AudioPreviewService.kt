package org.bscm.services

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.logging.*
import org.bscm.services.preview.resolvers.PreviewResolverRegistry
import org.bscm.services.track.TrackInfoResult
import org.bscm.storage.StorageService
import org.bscm.utils.MediaConverter
import java.util.*

private val log = KtorSimpleLogger("AudioPreviewService")

class AudioPreviewService(
    private val registry: PreviewResolverRegistry,
    private val storageService: StorageService,
    private val client: HttpClient
) {

    /**
     * Resolves, downloads, converts, and uploads the audio preview for a track.
     * Returns the CDN URL on success, null on failure.
     */
    suspend fun publish(trackId: UUID, mediaInfo: TrackInfoResult): String? {
        val provider = mediaInfo.previewProvider ?: return null
        val providerTrackId = mediaInfo.previewProviderTrackId ?: return null

        return try {
            val resolver = registry.get(provider)
            val preview = resolver.resolve(providerTrackId) ?: return null

            val audioBytes = downloadAudio(preview.url) ?: return null

            val opusBytes = MediaConverter.convertToOpus(audioBytes) ?: return null

            storageService.uploadTrackPreview(trackId, opusBytes)

            storageService.trackPreviewUrl(trackId).also {
                log.info("Published preview for track $trackId: $it")
            }
        } catch (e: Exception) {
            log.warn("Failed to publish preview for track $trackId: ${e.message}")
            null
        }
    }

    private suspend fun downloadAudio(url: String): ByteArray? {
        return try {
            val response = client.get(url)
            if (!response.status.isSuccess()) {
                log.warn("Failed to download preview from $url: ${response.status}")
                return null
            }
            response.readRawBytes()
        } catch (e: Exception) {
            log.warn("Failed to download preview from $url: ${e.message}")
            null
        }
    }
}
