package org.bscm.repository.implementation

import io.ktor.server.plugins.*
import org.bscm.models.Account
import org.bscm.models.User
import org.bscm.models.dao.AccountEntity
import org.bscm.models.dao.UserEntity
import org.bscm.models.dto.account.CreateAccountRequest
import org.bscm.models.dto.user.CreateUserRequest
import org.bscm.models.dto.user.UpdateUserRequest
import org.bscm.models.tables.AccountTable
import org.bscm.models.tables.UserTable
import org.bscm.repository.UserRepository
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.upsert
import java.time.LocalDate
import java.util.*

class UserRepositoryImpl : UserRepository {
    companion object {
        fun userEntityToUser(entity: UserEntity, includeAccounts: Boolean = false): User = User(
            id = entity.id.value,
            username = entity.username,
            email = entity.email,
            imageUrl = entity.imageUrl,
            discordId = entity.discordId,
            createdAt = entity.createdAt,
            accounts = if (includeAccounts) {
                entity.accounts.map { accountEntityToAccount(it) }
            } else {
                emptyList()
            }
        )

        fun accountEntityToAccount(entity: AccountEntity): Account = Account(
            id = entity.id.value,
            provider = entity.provider,
            // providerAccountId = entity.providerAccountId,
            refreshToken = entity.refreshToken,
            accessToken = entity.accessToken,
            expiresAt = entity.expiresAt,
            tokenType = entity.tokenType,
            scope = entity.scope,
        )
    }


    override suspend fun getUsers(query: String?): List<User> = newSuspendedTransaction {
        // If query is null, return all users
        if (query.isNullOrBlank()) {
            UserEntity.all().map(::userEntityToUser)
        } else {
            // Otherwise, return users that match the query
            UserEntity.find { UserTable.username like "%$query%" }.map(::userEntityToUser)
        }
    }

    override suspend fun getUserByDiscordId(discordId: String): User? = newSuspendedTransaction {
        UserEntity.find { UserTable.discordId eq discordId }.singleOrNull()?.let(::userEntityToUser)
    }

    override suspend fun getUserByUsername(username: String): User? = newSuspendedTransaction {
        UserEntity.find { UserTable.username eq username }.singleOrNull()?.let(::userEntityToUser)
    }

    override suspend fun createUser(user: CreateUserRequest): User = newSuspendedTransaction {
        val newUser = UserEntity.new(UUID.randomUUID()) {
            this.username = user.username
            this.email = user.email
            this.discordId = user.discordId
            this.imageUrl = user.imageUrl
            this.createdAt = LocalDate.now()
        }
        userEntityToUser(newUser)
    }

    override suspend fun updateUser(id: UUID, user: UpdateUserRequest): User = newSuspendedTransaction {
        val existingUser = UserEntity.findById(id) ?: throw NotFoundException("User not found")
        existingUser.apply {
            username = user.username.let { if (it.isNullOrBlank()) username else it }
            email = user.email.let { if (it.isNullOrBlank()) email else it }
            imageUrl = user.imageUrl.let { if (it.isNullOrBlank()) imageUrl else it }
        }
        userEntityToUser(existingUser, true)
    }

    override suspend fun deleteUser(id: UUID): Boolean = newSuspendedTransaction {
        val user = UserEntity.findById(id) ?: return@newSuspendedTransaction false
        user.delete()
        true
    }

    override suspend fun upsertAccount(id: UUID, account: CreateAccountRequest) = newSuspendedTransaction {
        UserEntity.findById(id) ?: throw NotFoundException("User not found")

        AccountTable.upsert {
            it[provider] = account.provider
            // it[providerAccountId] = account.providerAccountId
            it[refreshToken] = account.refreshToken
            it[accessToken] = account.accessToken
            it[expiresAt] = account.expiresAt
            it[tokenType] = account.tokenType
            it[scope] = account.scope
            it[userId] = id
        }

        true
    }

    override suspend fun deleteAccount(id: UUID): Boolean = newSuspendedTransaction {
        AccountTable.select(AccountTable.id).where { AccountTable.userId eq id }.singleOrNull()
            ?: throw NotFoundException("Account not found for user ID: $id")

        AccountTable.deleteWhere { AccountTable.userId eq id }

        true
    }
}
