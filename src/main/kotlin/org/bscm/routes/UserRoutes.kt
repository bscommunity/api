package org.bscm.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.bscm.models.dto.user.CreateUserRequest
import org.bscm.models.dto.user.UpdateUserRequest
import org.bscm.models.repository.IUserRepository
import java.util.*

fun Route.userRoutes(userRepository: IUserRepository) {
    route("/users") {
        // Create a new user
        post {
            val createRequest = call.receive<CreateUserRequest>()
            val createdUser = userRepository.createUser(createRequest)
            call.respond(HttpStatusCode.Created, createdUser)
        }

        authenticate("auth-bearer") {
            // Get all users
            get {
                // Get query parameters (search)
                val search = call.request.queryParameters["search"]

                val users = userRepository.getUsers(search)

                call.respond(users)
            }

            get("/hello") {
                val principal = call.principal<JWTPrincipal>()
                val username = principal!!.payload.getClaim("username").asString()
                val expiresAt = principal.expiresAt?.time?.minus(System.currentTimeMillis())
                call.respondText("Hello, $username! Token is expired at $expiresAt ms.")
            }

            // Get user by Discord ID
            get("{id}") {
                val id = call.parameters["id"] ?: throw IllegalArgumentException("Invalid or missing Discord ID")

                val user = userRepository.getUserByDiscordId(id)
                if (user != null) {
                    call.respond(user)
                } else {
                    throw NotFoundException("User not found")
                }
            }

            // Get user by username
            get("by-username/{username}") {
                val username = call.parameters["username"] ?: throw IllegalArgumentException("Invalid or missing username")

                val user = userRepository.getUserByUsername(username)
                if (user != null) {
                    call.respond(user)
                } else {
                    throw NotFoundException("User not found")
                }
            }

            // Update an existing user
            put("{id}") {
                val id = call.parameters["id"]?.let { UUID.fromString(it) }
                if (id == null) {
                    throw IllegalArgumentException("Invalid or missing ID")
                }
                val user = call.receive<UpdateUserRequest>()

                try {
                    userRepository.updateUser(id, user)
                } catch (e: NotFoundException) {
                    throw NotFoundException(e.message ?: "Not Found")
                }
            }

            // Delete a user
            delete("{id}") {
                val id = call.parameters["id"]?.let { UUID.fromString(it) }
                if (id == null) {
                    throw IllegalArgumentException("Invalid or missing ID")
                }
                val deleted = userRepository.deleteUser(id)
                if (deleted) {
                    call.respond(HttpStatusCode.OK, "User deleted successfully")
                } else {
                    throw NotFoundException("User not found")
                }
            }
        }
    }
}