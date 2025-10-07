
package org.bscm.models.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.datetime

object CollectionTable : UUIDTable("collections") {
    val userId = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)

    val name = varchar("name", 30)
    val isPublic = bool("is_public").default(false)

    val createdAt = datetime("created_at")
    val updatedAt = datetime("updated_at")

    init {
        uniqueIndex(userId, name)

        index(false, userId)
        index(false, userId, name)
    }
}
