package org.bscm.services

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap

enum class PublishStep(val key: String, val message: String) {
    EXTRACTING_BUNDLE("extracting_bundle", "Extracting bundle data…"),
    PARSING_CHART("parsing_chart", "Parsing chart…"),
    RESOLVING_METADATA("resolving_metadata", "Resolving track metadata…"),
    FETCHING_MEDIA_INFO("fetching_media_info", "Fetching media information…"),
    CREATING_CHART("creating_chart", "Creating chart in database…"),
    PREPARING_BUNDLE("preparing_bundle", "Preparing bundle…"),
    UPLOADING_TO_DISCORD("uploading_to_discord", "Uploading to Discord…"),
    FINALIZING_VERSION("finalizing_version", "Finalizing version…"),
    UPLOADING_COVER("uploading_cover", "Uploading cover image…"),
    GENERATING_PREVIEW("generating_preview", "Generating audio preview…"),
    LOGGING_ACTIVITY("logging_activity", "Saving activity…"),
    COMPLETED("completed", "Done!"),
}

data class PublishEvent(
    val sessionId: String,
    val step: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
)

class PublishEventService {
    private val sessions = ConcurrentHashMap<String, MutableSharedFlow<PublishEvent>>()

    private fun getOrCreateFlow(sessionId: String): MutableSharedFlow<PublishEvent> {
        return sessions.getOrPut(sessionId) {
            MutableSharedFlow(extraBufferCapacity = 64)
        }
    }

    fun emit(sessionId: String, step: PublishStep) {
        val flow = sessions[sessionId] ?: return
        val event = PublishEvent(
            sessionId = sessionId,
            step = step.key,
            message = step.message,
        )
        tryEmit(flow, event)
    }

    fun emit(sessionId: String, step: PublishStep, customMessage: String) {
        val flow = sessions[sessionId] ?: return
        val event = PublishEvent(
            sessionId = sessionId,
            step = step.key,
            message = customMessage,
        )
        tryEmit(flow, event)
    }

    fun complete(sessionId: String) {
        val flow = sessions[sessionId] ?: return
        val event = PublishEvent(
            sessionId = sessionId,
            step = PublishStep.COMPLETED.key,
            message = PublishStep.COMPLETED.message,
        )
        tryEmit(flow, event)
    }

    fun events(sessionId: String): Flow<PublishEvent> {
        return getOrCreateFlow(sessionId).asSharedFlow()
    }

    fun cleanup(sessionId: String) {
        sessions.remove(sessionId)
    }

    private fun tryEmit(flow: MutableSharedFlow<PublishEvent>, event: PublishEvent) {
        flow.tryEmit(event)
    }
}
