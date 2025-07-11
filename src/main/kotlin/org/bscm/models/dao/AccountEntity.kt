package org.bscm.models.dao

import org.bscm.models.tables.AccountTable
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.*

// "Entity" is equivalent to DAO (Data Access Object) in Exposed
class AccountEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<AccountEntity>(AccountTable)

    var provider by AccountTable.provider
    // var providerAccountId by AccountTable.providerAccountId
    var refreshToken by AccountTable.refreshToken
    var accessToken by AccountTable.accessToken
    var expiresAt by AccountTable.expiresAt
    var tokenType by AccountTable.tokenType
    var scope by AccountTable.scope

    var user by UserEntity referencedOn AccountTable.userId
}