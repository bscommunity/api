package org.bscm.models.tables

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.bscm.models.enums.CatalogItemStatus
import org.bscm.models.enums.CatalogItemType
import org.bscm.models.enums.Visibility
import org.bscm.utils.NanoIdUtils
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.datetime.datetime

object CatalogItemTable : IdTable<String>("catalog_items") {
    override val id = varchar("id", 10)
        .clientDefault { NanoIdUtils.generateOptimized(10, "_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ", 63, 16) }
        .entityId()
    val type = enumerationByName("type", 20, CatalogItemType::class)
    val status = enumerationByName("status", 20, CatalogItemStatus::class)
    val visibility = enumerationByName("visibility", 20, Visibility::class)
        .default(Visibility.PUBLIC)

    val versionsCount =
        integer("versions_count")
            .default(0)

    val latestVersionId =
        reference(
            "latest_version_id",
            VersionTable,
            onDelete = ReferenceOption.SET_NULL
        ).nullable()

    val previewVideoId = varchar("preview_video_id", 12).nullable()

    val isFeatured = bool("is_featured").default(false)

    val downloadsSum = integer("downloads_sum").default(0)

    val discordChannelId = varchar("discord_channel_id", 255).nullable()
    val discordMessageId = varchar("discord_message_id", 255).nullable()

    val createdAt = datetime("created_at").clientDefault { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()) }
    val publishedAt = datetime("published_at").nullable()
    val updatedAt = datetime("updated_at").nullable()

    val authorId = reference("author_id", UserTable, ReferenceOption.SET_NULL).nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index(false, type)
        index(false, status)
        index(false, authorId)
    }
}
