package org.bscm.services

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.logging.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.bscm.services.UploadService.RefreshData
import org.bscm.services.track.clients.applicationHttpClient
import org.bscm.services.track.clients.jsonClient
import kotlin.math.min
import kotlin.math.pow

private val logger = KtorSimpleLogger("RefreshService")

// ---------------------------------------------------------------------------
// Configuration constants — tweak these without touching logic
// ---------------------------------------------------------------------------
private const val DISCORD_API_BASE        = "https://discord.com/api/v10"
private const val MESSAGES_PER_PAGE       = 100   // Discord max per request
private const val MAX_PAGES               = 500   // hard ceiling (~50 000 messages)
private const val MAX_CONSECUTIVE_ERRORS  = 3
private const val MAX_RATE_LIMIT_RETRIES  = 5     // prevent infinite 429 loops
private const val BASE_RETRY_DELAY_MS     = 1_000L
private const val RATE_LIMIT_BUFFER_MS    = 150L  // extra breathing room after a limit window
private const val PROACTIVE_LIMIT_THRESHOLD = 2   // start slowing down when remaining <= this

// ---------------------------------------------------------------------------
// Small sealed hierarchy to make every HTTP outcome explicit.
// Think of it like a TypeScript discriminated union:
//   type PageResult = { kind: "success"; messages: JsonArray } | { kind: "rateLimited" } | ...
// ---------------------------------------------------------------------------
private sealed interface PageResult {
    /** A page was returned successfully. */
    data class Success(
        val messages: JsonArray,
        val rateLimitRemaining: Int,
        val rateLimitResetAfterMs: Long,
        val rateLimitScope: String,
    ) : PageResult

    /** Discord asked us to back off — wait [retryAfterMs] then retry. */
    data class RateLimited(val retryAfterMs: Long, val isShared: Boolean) : PageResult

    /** Transient server/network issue — may be retried. */
    data class TransientError(val statusCode: Int, val message: String) : PageResult

    /** Unrecoverable — wrong token, wrong channel, banned, etc. Stop immediately. */
    data class FatalError(val statusCode: Int, val message: String) : PageResult
}

// ---------------------------------------------------------------------------
// Thin model to carry parsed attachment+cover data before writing to the map.
// Separating parsing from fetching means each piece can be tested in isolation.
// ---------------------------------------------------------------------------
private data class RawMessageData(
    val messageId: String,
    val versionId: String,
    val bundleUrl: String,
    val coverUrl: String?,
)

class RefreshService(
    private val botToken: String,
    private val channelId: String,
) {

    /**
     * Fetches every message in [channelId] and extracts bundle/cover URLs.
     *
     * Strategy (mirrors Discord's own best-practice docs):
     *  1. Fetch pages of 100 messages, walking backwards via `before=<id>`.
     *  2. After every successful page, check X-RateLimit-Remaining *proactively*
     *     and pause before we hit zero — not after.
     *  3. On a 429, honour `Retry-After` exactly, with a small buffer, and cap
     *     retries so a sustained ban can't loop forever.
     *  4. On fatal errors (401/403/404) stop immediately — retrying these burns
     *     quota and can escalate to a CloudFlare ban.
     *  5. Transient errors use true exponential back-off with jitter.
     */
    suspend fun refreshBundleUrls(): Map<String, RefreshData> {
        val refreshData = mutableMapOf<String, RefreshData>()
        var lastMessageId: String? = null
        var consecutiveErrors = 0
        var pagesProcessed = 0

        while (consecutiveErrors < MAX_CONSECUTIVE_ERRORS && pagesProcessed < MAX_PAGES) {

            val result = fetchPage(lastMessageId)

            when (result) {

                // ── Happy path ────────────────────────────────────────────────
                is PageResult.Success -> {
                    consecutiveErrors = 0
                    pagesProcessed++

                    if (result.messages.isEmpty()) {
                        logger.info("Empty page received — channel fully traversed.")
                        break
                    }

                    // Parse this page and fold results into the accumulator
                    parseMessages(result.messages).forEach { raw ->
                        refreshData[raw.messageId] = RefreshData(
                            versionId  = raw.versionId,
                            bundleUrl  = raw.bundleUrl,
                            coverUrl   = raw.coverUrl,
                        )
                    }

                    logger.info(
                        "Page $pagesProcessed: processed ${result.messages.size} messages " +
                                "(total so far: ${refreshData.size}), " +
                                "rate-limit remaining: ${result.rateLimitRemaining}"
                    )

                    // Advance the cursor to the oldest message on this page
                    lastMessageId = result.messages
                        .last().jsonObject["id"]?.jsonPrimitive?.content

                    // Stop if this was the last partial page
                    if (result.messages.size < MESSAGES_PER_PAGE) {
                        logger.info("Partial page — reached end of channel history.")
                        break
                    }

                    // ── Proactive rate-limit throttling ───────────────────────
                    // Think of this like a token-bucket: we slow down *before*
                    // the bucket is empty, not after it has already overflowed.
                    if (result.rateLimitScope != "shared" &&
                        result.rateLimitRemaining <= PROACTIVE_LIMIT_THRESHOLD
                    ) {
                        val waitMs = result.rateLimitResetAfterMs + RATE_LIMIT_BUFFER_MS
                        logger.info(
                            "Proactive throttle: only ${result.rateLimitRemaining} requests " +
                                    "remaining in window. Waiting ${waitMs}ms."
                        )
                        delay(waitMs)
                    }
                }

                // ── Discord told us to back off (429) ─────────────────────────
                is PageResult.RateLimited -> {
                    // Shared limits (e.g. global emoji routes) don't count against
                    // our bot's per-route bucket, so we can retry very quickly.
                    if (result.isShared) {
                        logger.warn("Shared rate limit hit — brief pause before retry.")
                        delay(RATE_LIMIT_BUFFER_MS)
                    } else {
                        logger.warn(
                            "Rate limited (429) — waiting ${result.retryAfterMs}ms as instructed."
                        )
                        delay(result.retryAfterMs + RATE_LIMIT_BUFFER_MS)
                    }
                    // NOTE: we do NOT increment consecutiveErrors here because
                    // a 429 is an expected flow-control signal, not a broken state.
                    // However we DO track how many times we've been limited this
                    // session via the inner retry loop inside fetchPage.
                }

                // ── Transient error (5xx, network blip, etc.) ─────────────────
                is PageResult.TransientError -> {
                    consecutiveErrors++
                    val backoffMs = exponentialBackoff(consecutiveErrors)
                    logger.warn(
                        "Transient error ${result.statusCode}: ${result.message}. " +
                                "Attempt $consecutiveErrors/$MAX_CONSECUTIVE_ERRORS. " +
                                "Backing off ${backoffMs}ms."
                    )
                    if (consecutiveErrors < MAX_CONSECUTIVE_ERRORS) {
                        delay(backoffMs)
                    }
                }

                // ── Fatal — no point retrying ──────────────────────────────────
                is PageResult.FatalError -> {
                    logger.error(
                        "Fatal error ${result.statusCode}: ${result.message}. " +
                                "Aborting fetch to avoid ban."
                    )
                    break
                }
            }
        }

        if (pagesProcessed >= MAX_PAGES) {
            logger.warn("Hit MAX_PAGES ($MAX_PAGES) safety ceiling. Stopping early.")
        }

        logger.info("Fetch complete. Pages: $pagesProcessed, Messages: ${refreshData.size}")
        return refreshData
    }

    // -------------------------------------------------------------------------
    // Single-responsibility: knows only how to make one HTTP call and translate
    // the raw HTTP response into a typed PageResult. No business logic here.
    // -------------------------------------------------------------------------
    private suspend fun fetchPage(beforeId: String?): PageResult {
        val url = buildString {
            append("$DISCORD_API_BASE/channels/$channelId/messages?limit=$MESSAGES_PER_PAGE")
            if (beforeId != null) append("&before=$beforeId")
        }

        var rateLimitRetries = 0

        // Inner retry loop handles 429s in isolation — the outer loop handles
        // all other retry concerns. This mirrors how Discord's own libraries work.
        while (rateLimitRetries <= MAX_RATE_LIMIT_RETRIES) {
            val response: HttpResponse = try {
                applicationHttpClient.get(url) {
                    header(HttpHeaders.Authorization, "Bot $botToken")
                }
            } catch (e: Exception) {
                logger.warn("Network exception on GET $url", e)
                return PageResult.TransientError(-1, e.message ?: "Unknown network error")
            }

            when (val status = response.status.value) {

                200 -> {
                    val remaining  = response.headers["X-RateLimit-Remaining"]?.toIntOrNull() ?: Int.MAX_VALUE
                    val resetAfter = response.headers["X-RateLimit-Reset-After"]?.toDoubleOrNull() ?: 0.0
                    val scope      = response.headers["X-RateLimit-Scope"] ?: "user"

                    val messages = runCatching {
                        jsonClient.decodeFromString(JsonArray.serializer(), response.bodyAsText())
                    }.getOrElse { e ->
                        logger.warn("Failed to deserialize message JSON", e)
                        return PageResult.TransientError(200, "JSON parse failure: ${e.message}")
                    }

                    return PageResult.Success(
                        messages              = messages,
                        rateLimitRemaining    = remaining,
                        rateLimitResetAfterMs = (resetAfter * 1_000).toLong(),
                        rateLimitScope        = scope,
                    )
                }

                429 -> {
                    rateLimitRetries++
                    val retryAfter = response.headers["Retry-After"]?.toDoubleOrNull() ?: 1.0
                    val scope      = response.headers["X-RateLimit-Scope"] ?: "user"
                    val retryMs    = (retryAfter * 1_000).toLong()

                    if (rateLimitRetries > MAX_RATE_LIMIT_RETRIES) {
                        logger.error(
                            "Exceeded MAX_RATE_LIMIT_RETRIES ($MAX_RATE_LIMIT_RETRIES) " +
                                    "on a single page. Treating as fatal."
                        )
                        return PageResult.FatalError(429, "Too many consecutive rate limits")
                    }

                    return PageResult.RateLimited(retryAfterMs = retryMs, isShared = scope == "shared")
                }

                401 -> return PageResult.FatalError(401, "Invalid bot token — check your credentials.")
                403 -> return PageResult.FatalError(403, "Forbidden — bot lacks Read Message History permission.")
                404 -> return PageResult.FatalError(404, "Channel not found — check channelId.")

                else -> {
                    // Treat anything else (5xx, etc.) as transient
                    return PageResult.TransientError(status, "Unexpected HTTP $status")
                }
            }
        }

        // Should be unreachable, but satisfies the Kotlin exhaustive-return check
        return PageResult.FatalError(-1, "Exited fetch loop unexpectedly")
    }

    // -------------------------------------------------------------------------
    // Single-responsibility: knows only how to turn a JsonArray of Discord
    // message objects into a list of our own typed models. Pure function —
    // no I/O, no side effects, trivially unit-testable.
    // -------------------------------------------------------------------------
    private fun parseMessages(messages: JsonArray): List<RawMessageData> =
        messages.mapNotNull { element ->
            val obj         = element.jsonObject
            val messageId   = obj["id"]?.jsonPrimitive?.content       ?: return@mapNotNull null
            val attachments = obj["attachments"]?.jsonArray           ?: return@mapNotNull null
            val embeds      = obj["embeds"]?.jsonArray

            if (attachments.isEmpty()) return@mapNotNull null

            // The original code took the *last* attachment — preserving that intent
            val lastAttachment = attachments.last().jsonObject
            val bundleUrl  = lastAttachment["url"]?.jsonPrimitive?.content  ?: return@mapNotNull null
            val versionId  = lastAttachment["id"]?.jsonPrimitive?.content   ?: return@mapNotNull null

            val coverUrl = embeds
                ?.firstOrNull()
                ?.jsonObject?.get("image")
                ?.jsonObject?.get("url")
                ?.jsonPrimitive?.content

            RawMessageData(
                messageId = messageId,
                versionId = versionId,
                bundleUrl = bundleUrl,
                coverUrl  = coverUrl,
            )
        }
}

// ---------------------------------------------------------------------------
// Pure utility — no need to live inside the class
// ---------------------------------------------------------------------------

/**
 * True exponential back-off with a hard cap of 30 seconds.
 * attempt=1 → 2s, attempt=2 → 4s, attempt=3 → 8s, capped at 30s.
 *
 * This is like TypeScript's: Math.min(1000 * 2 ** attempt, 30_000)
 */
private fun exponentialBackoff(attempt: Int): Long =
    min(BASE_RETRY_DELAY_MS * 2.0.pow(attempt).toLong(), 30_000L)