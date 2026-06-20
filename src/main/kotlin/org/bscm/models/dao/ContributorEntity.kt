package org.bscm.models.dao

import org.bscm.models.tables.ContributorTable
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class ContributorEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<ContributorEntity>(ContributorTable)

    var catalogItem by CatalogItemEntity referencedOn ContributorTable.catalogItemId
    var user by UserEntity referencedOn ContributorTable.userId
    var role by ContributorTable.role
    var note by ContributorTable.note
    var joinedAt by ContributorTable.joinedAt
}
