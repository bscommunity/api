package org.bscm.repository

import org.bscm.models.User
import org.bscm.models.dto.CreateUserRequest
import org.bscm.models.entities.UserEntity
import org.bscm.models.tables.UserTable
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

class UserRepositoryImpl : UserRepository {

    private fun userEntityToUser(entity: UserEntity) = User(
        id = entity.id.value,
        username = entity.username,
        email = entity.email,
        imageUrl = entity.imageUrl,
        createdAt = entity.createdAt
    )

    override suspend fun getAllUsers(): List<User> = newSuspendedTransaction {
        UserEntity.all().map(::userEntityToUser)
    }

    override suspend fun getUserById(id: UUID): User? = newSuspendedTransaction {
        UserEntity.findById(id)?.let(::userEntityToUser)
    }

    override suspend fun getUserByUsername(username: String): User? = newSuspendedTransaction {
        UserEntity.find { UserTable.username eq username }.singleOrNull()?.let(::userEntityToUser)
    }

    override suspend fun createUser(user: User): User = newSuspendedTransaction {
        val newUser = UserEntity.new(user.id) {
            this.username = user.username
            this.email = user.email
            this.imageUrl = user.imageUrl
            this.createdAt = user.createdAt
        }
        userEntityToUser(newUser)
    }

    override suspend fun updateUser(id: UUID, user: CreateUserRequest): Boolean = newSuspendedTransaction {
        val existingUser = UserEntity.findById(id) ?: return@newSuspendedTransaction false
        existingUser.apply {
            username = user.username
            email = user.email
            imageUrl = user.imageUrl
        }
        true
    }

    override suspend fun deleteUser(id: UUID): Boolean = newSuspendedTransaction {
        val user = UserEntity.findById(id) ?: return@newSuspendedTransaction false
        user.delete()
        true
    }
}
