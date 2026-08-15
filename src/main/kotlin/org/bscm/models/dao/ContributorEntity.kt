package org.bscm.models.dao

import org.bscm.models.tables.ContributorTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass

class ContributorEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<ContributorEntity>(ContributorTable)

    var catalogItem by CatalogItemEntity referencedOn ContributorTable.catalogItemId
    var user by UserEntity referencedOn ContributorTable.userId
    var role by ContributorTable.role
    var note by ContributorTable.note
    var joinedAt by ContributorTable.joinedAt
}
