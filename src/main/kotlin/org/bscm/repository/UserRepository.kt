package org.bscm.repository

import org.bscm.models.User
import org.bscm.models.dto.account.CreateAccountRequest
import org.bscm.models.dto.user.CreateUserRequest
import org.bscm.models.dto.user.UpdateUserRequest
import java.util.*

interface UserRepository {
    suspend fun getUsers(query: String?): List<User>
    suspend fun getUserById(id: UUID): User?
    suspend fun getUserByDiscordId(discordId: String): User?
    suspend fun getUserByUsername(username: String): User?
    suspend fun createUser(user: CreateUserRequest): User
    suspend fun updateUser(id: UUID, user: UpdateUserRequest): User
    suspend fun deleteUser(id: UUID): Boolean
    suspend fun upsertAccount(id: UUID, account: CreateAccountRequest): Boolean
    suspend fun deleteAccount(id: UUID): Boolean
}
