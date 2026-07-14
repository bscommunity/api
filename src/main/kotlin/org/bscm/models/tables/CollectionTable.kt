package org.bscm.models.tables

import org.bscm.models.enums.CollectionKind
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.java.UUIDTable
import org.jetbrains.exposed.v1.datetime.datetime

object CollectionTable : UUIDTable("collections") {
    val userId = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val kind = enumerationByName("kind", 20, CollectionKind::class).default(CollectionKind.USER)
    val slug = varchar("slug", 30).uniqueIndex().nullable()

    val name = varchar("name", 30)
    val isPublic = bool("is_public").default(false)

    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")

    init {
        // TODO: Add unique index for (userId, kind) when kind is not CollectionKind.USER
        index(true, userId, name)
    }
}
