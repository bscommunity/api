package org.bscm.repository

import org.bscm.models.User
import org.bscm.models.dto.CreateUserRequest
import org.bscm.models.dto.UpdateUserRequest
import java.util.UUID

interface UserRepository {
    suspend fun getAllUsers(): List<User>
    suspend fun getUserByDiscordId(discordId: String): User?
    suspend fun getUserByUsername(username: String): User?
    suspend fun createUser(user: CreateUserRequest): User
    suspend fun updateUser(id: UUID, user: UpdateUserRequest): User
    suspend fun deleteUser(id: UUID): Boolean
}
