package org.bscm.models.dao

import org.bscm.models.tables.BadgeTable
import org.bscm.models.tables.UserBadgeTable
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.*

// "Entity" is equivalent to DAO (Data Access Object) in Exposed
class BadgeEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<BadgeEntity>(BadgeTable)

    var name by BadgeTable.name
    var description by BadgeTable.description
    var criteria by BadgeTable.criteria
    val createdAt by BadgeTable.createdAt

    val users by UserEntity.via(
        UserBadgeTable.badgeId,
        UserBadgeTable.userId
    )
}