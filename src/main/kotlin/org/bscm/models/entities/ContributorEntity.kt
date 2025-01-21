package org.bscm.models.entities

import org.bscm.models.tables.ContributorTable
import org.jetbrains.exposed.dao.CompositeEntity
import org.jetbrains.exposed.dao.CompositeEntityClass
import org.jetbrains.exposed.dao.id.CompositeID
import org.jetbrains.exposed.dao.id.EntityID

class ContributorEntity(id: EntityID<CompositeID>) : CompositeEntity(id) {
    companion object : CompositeEntityClass<CompositeEntity>(ContributorTable)

    var role by ContributorTable.role
    var joinedAt by ContributorTable.joinedAt
}