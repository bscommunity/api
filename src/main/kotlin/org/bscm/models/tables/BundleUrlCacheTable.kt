package org.bscm.models.tables

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.datetime
import kotlin.time.Clock

object BundleUrlCacheTable : Table("bundle_url_cache") {

    val catalogItemId =
        reference(
            "catalog_item_id",
            CatalogItemTable,
            onDelete = ReferenceOption.CASCADE
        )

    val bundleUrl = text("bundle_url")
    val expiresAt = datetime("expires_at")

    val lastValidatedAt =
        datetime("last_validated_at")
            .clientDefault { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) }

    override val primaryKey =
        PrimaryKey(catalogItemId)
}