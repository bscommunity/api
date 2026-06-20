package org.bscm.models.tables

import org.jetbrains.exposed.dao.id.ULongIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

object VersionTable : ULongIdTable("catalog_item_versions") {
    val catalogItemId = reference("catalog_item_id", CatalogItemTable, onDelete = ReferenceOption.CASCADE).index()

    val versionCode = integer("version_code")
    val downloadsAmount = integer("downloads_amount").default(0)

    val fileSizeBytes = long("file_size_bytes")
    val changelog = text("changelog").nullable()

    val discordAttachmentId = varchar("discord_attachment_id", 255).nullable()

    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)

    init {
        uniqueIndex(catalogItemId, versionCode)
    }
}
