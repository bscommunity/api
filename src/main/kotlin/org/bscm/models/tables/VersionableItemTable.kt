package org.bscm.models.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.IdTable

object VersionableItemTable : IdTable<String>("versionable_items") {
    override val id = reference("catalog_item_id", CatalogItemTable)
    val versionsCount = integer("versions_count").default(0)
    val latestVersionId = reference("latest_version_id", VersionTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val bundleHash = varchar("bundle_hash", 64).uniqueIndex().nullable()

    override val primaryKey = PrimaryKey(id)
}
