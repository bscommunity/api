package org.bscm.repository

import org.bscm.models.User
import java.util.UUID

interface UserRepository {
    suspend fun getAllUsers(): List<User>
    suspend fun getUserById(id: UUID): User?
    suspend fun getUserByUsername(username: String): User?
    suspend fun createUser(user: User): User
    suspend fun updateUser(id: UUID, user: User): Boolean
    suspend fun deleteUser(id: UUID): Boolean
}
