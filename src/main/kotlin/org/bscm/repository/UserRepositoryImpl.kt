package org.bscm.repository

import io.ktor.server.plugins.*
import org.bscm.models.User
import org.bscm.models.dto.CreateUserRequest
import org.bscm.models.dto.UpdateUserRequest
import org.bscm.models.entities.UserEntity
import org.bscm.models.tables.UserTable
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDateTime
import java.util.*

class UserRepositoryImpl : UserRepository {

    private fun userEntityToUser(entity: UserEntity): User = User(
        id = entity.id.value,
        username = entity.username,
        email = entity.email,
        imageUrl = entity.imageUrl,
        discordId = entity.discordId,
        createdAt = entity.createdAt
    )

    override suspend fun getAllUsers(): List<User> = newSuspendedTransaction {
        UserEntity.all().map(::userEntityToUser)
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
            this.createdAt = LocalDateTime.now()
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
        userEntityToUser(existingUser)
    }

    override suspend fun deleteUser(id: UUID): Boolean = newSuspendedTransaction {
        val user = UserEntity.findById(id) ?: return@newSuspendedTransaction false
        user.delete()
        true
    }
}
