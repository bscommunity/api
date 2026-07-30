package org.bscm.models.tables

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.datetime.datetime
import kotlin.time.Clock

object ChangelogTable : UUIDTable("changelogs") {
    val catalogItemId = reference("catalog_item_id", VersionableItemTable, onDelete = ReferenceOption.CASCADE)
    val description = varchar("description", 1000)
    val createdAt = datetime("created_at")
        .clientDefault { Clock.System.now().toLocalDateTime(TimeZone.UTC) }
}
