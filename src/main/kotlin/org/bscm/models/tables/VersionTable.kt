package org.bscm.models.tables

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.ULongIdTable
import org.jetbrains.exposed.v1.datetime.datetime
import kotlin.time.Clock

object VersionTable : ULongIdTable("catalog_item_versions") {
    val catalogItemId = reference("catalog_item_id", CatalogItemTable, onDelete = ReferenceOption.CASCADE).index()

    val versionCode = integer("version_code")
    val downloadsAmount = integer("downloads_amount").default(0)

    val fileSizeBytes = long("file_size_bytes")
    val changelog = text("changelog").nullable()

    val discordAttachmentId = varchar("discord_attachment_id", 255).nullable()

    val createdAt = datetime("created_at").clientDefault { Clock.System.now().toLocalDateTime(TimeZone.UTC) }

    init {
        uniqueIndex(catalogItemId, versionCode)
    }
}
