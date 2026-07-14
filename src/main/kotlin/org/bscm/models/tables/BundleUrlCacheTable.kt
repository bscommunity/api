package org.bscm.models.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.CurrentDateTime
import org.jetbrains.exposed.v1.javatime.datetime

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
            .defaultExpression(CurrentDateTime)

    override val primaryKey =
        PrimaryKey(catalogItemId)
}