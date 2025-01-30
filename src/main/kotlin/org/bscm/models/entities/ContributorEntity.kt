package org.bscm.models.entities

import org.bscm.models.tables.ContributorTable
import org.jetbrains.exposed.dao.CompositeEntity
import org.jetbrains.exposed.dao.CompositeEntityClass
import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.dao.id.EntityID

class ContributorEntity(id: EntityID<CompositeID>) : CompositeEntity(id) {
    companion object : CompositeEntityClass<ContributorEntity>(ContributorTable)

    var user by UserEntity referencedOn ContributorTable.userId
    var roles by ContributorTable.roles
    var joinedAt by ContributorTable.joinedAt
}