package org.bscm.models.entities

import org.bscm.models.tables.UserTable
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

// "Entity" is equivalent to DAO (Data Access Object) in Exposed
class UserEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<UserEntity>(UserTable)

    var username by UserTable.username
    var email by UserTable.email
    var imageUrl by UserTable.imageUrl
    var createdAt by UserTable.createdAt
}