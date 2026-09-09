package org.bscm.models.tables

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.bscm.models.enums.CollectionKind
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.datetime.datetime
import kotlin.time.Clock

object CollectionTable : UUIDTable("collections") {
    val userId = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val kind = enumeration("kind", CollectionKind::class).default(CollectionKind.USER)
    val slug = varchar("slug", 30).uniqueIndex().nullable()

    val name = varchar("name", 30)
    val isPublic = bool("is_public").default(false)

    val createdAt = datetime("created_at")
        .clientDefault { Clock.System.now().toLocalDateTime(TimeZone.UTC) }
    val updatedAt = datetime("updated_at")
        .clientDefault { Clock.System.now().toLocalDateTime(TimeZone.UTC) }

    init {
        // TODO: Add unique index for (userId, kind) when kind is not CollectionKind.USER
        index(true, userId, name)
        index(false, userId, kind)
    }
}
