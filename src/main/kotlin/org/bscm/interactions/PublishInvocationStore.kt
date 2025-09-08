package org.bscm.interactions

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Armazena temporariamente dados de uma invocação do comando /publish quando o usuário opta por usar modal.
 * Chave = interactionId original (o Application Command que abriu o modal)
 */
object PublishInvocationStore {
    private data class Entry(
        val createdAt: Instant = Instant.now(),
        val bundleAttachment: AttachmentRef,
        val chartAttachment: AttachmentRef,
        val userId: String?
    ) {
        fun isExpired(now: Instant = Instant.now()) = createdAt.isBefore(now.minusSeconds(300)) // 5 min
    }

    data class AttachmentRef(
        val id: String,
        val filename: String?,
        val url: String?,
        val contentType: String?
    )

    private val map = ConcurrentHashMap<String, Entry>()

    fun put(interactionId: String, bundle: AttachmentRef, chart: AttachmentRef, userId: String?) {
        cleanup()
        map[interactionId] = Entry(bundleAttachment = bundle, chartAttachment = chart, userId = userId)
    }

    data class Retrieved(
        val bundle: AttachmentRef,
        val chart: AttachmentRef,
        val userId: String?
    )

    fun take(interactionId: String): Retrieved? {
        val entry = map.remove(interactionId) ?: return null
        if (entry.isExpired()) return null
        return Retrieved(entry.bundleAttachment, entry.chartAttachment, entry.userId)
    }

    private fun cleanup() {
        val now = Instant.now()
        map.entries.removeIf { it.value.isExpired(now) }
    }
}

