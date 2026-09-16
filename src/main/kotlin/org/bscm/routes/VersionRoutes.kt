package org.bscm.routes

import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.logging.*
import org.bscm.models.dto.version.BatchVersionIdsRequest
import org.bscm.models.interfaces.IVersionRepository

private val logger = KtorSimpleLogger("VersionRoutes")

/** Max catalog item IDs accepted per latest-versions batch request. */
private const val MAX_BATCH_IDS = 100

fun Route.versionRoutes(
    versionRepository: IVersionRepository,
) {
    route("/versions") {
        // -----------------------------------------------------------------
        // HMAC + public routes (mobile app update checks, all content types)
        // -----------------------------------------------------------------
        authenticate("auth-public", "auth-hmac") {
            rateLimit(RateLimitName("restricted")) {
                /**
                 * Returns the latest version for each of the given catalog item IDs,
                 * across all versionable content types (charts, themes).
                 * Used by the mobile app to check for updates on installed items
                 * in a single round-trip instead of one request per content type.
                 *
                 * Tour passes are never versionable and are silently skipped.
                 * Unknown IDs are skipped; an empty list is returned when nothing
                 * matches instead of 404 so a single local-only ID cannot fail
                 * the whole batch.
                 *
                 * Tag: Versions
                 *
                 * Query: ids [String] Comma-separated catalog item IDs (max 100).
                 *
                 * Responses:
                 *   - 200 application/json [Array] List of latest versions.
                 *
                 * Security: auth-hmac
                 */
                get("latest-versions") {
                    val ids = call.request.queryParameters["ids"]
                        ?.split(",")
                        ?.map { it.trim() }
                        ?.filter { it.isNotEmpty() }
                        ?: emptyList()

                    if (ids.size > MAX_BATCH_IDS) {
                        throw BadRequestException("Too many catalog item IDs (max $MAX_BATCH_IDS)")
                    }

                    logger.info("Fetching latest versions for ${ids.size} catalog item IDs")

                    call.respond(versionRepository.getLatestVersionsByCatalogItemIds(ids.distinct()))
                }

                /**
                 * Batch-resolve latest versions by catalog item IDs (update check).
                 *
                 * Prefer this over `GET /versions/latest-versions` when the
                 * installed library is large: a POST body avoids URL length
                 * limits. Unknown IDs are skipped and an empty list is returned
                 * when nothing matches.
                 *
                 * Tag: Versions
                 *
                 * Body: application/json [BatchVersionIdsRequest] ids (max 100).
                 *
                 * Responses:
                 *   - 200 application/json [Array] List of latest versions.
                 *   - 400 application/json [Error] Invalid or missing IDs.
                 *
                 * Security: auth-hmac
                 */
                post("latest-versions") {
                    val request = runCatching { call.receive<BatchVersionIdsRequest>() }.getOrNull()
                        ?: throw BadRequestException("Invalid batch request body")

                    val ids = request.ids.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

                    if (ids.size > MAX_BATCH_IDS) {
                        throw BadRequestException("Too many catalog item IDs (max $MAX_BATCH_IDS)")
                    }

                    logger.info("Fetching latest versions for ${ids.size} catalog item IDs (POST batch)")

                    call.respond(versionRepository.getLatestVersionsByCatalogItemIds(ids))
                }
            }
        }
    }
}
