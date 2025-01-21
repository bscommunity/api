package org.bscm.routes

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.bscm.models.dto.CreateUserRequest
import org.bscm.models.dto.UpdateUserRequest
import org.bscm.repository.UserRepository
import java.util.*

fun Route.userRoutes(userRepository: UserRepository) {
    route("/users") {
        // Create a new user
        post {
            val createRequest = call.receive<CreateUserRequest>()
            val createdUser = userRepository.createUser(createRequest)
            call.respond(HttpStatusCode.Created, createdUser)
        }

        authenticate("auth-jwt") {
            // Get all users
            get {
                val users = userRepository.getAllUsers()
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
                val id = call.parameters["id"]
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid or missing Discord ID")
                    return@get
                }

                val user = userRepository.getUserByDiscordId(id)
                if (user != null) {
                    call.respond(user)
                } else {
                    call.respond(HttpStatusCode.NotFound, "User not found")
                }
            }

            // Update an existing user
            put("{id}") {
                val id = call.parameters["id"]?.let { UUID.fromString(it) }
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid or missing ID")
                    return@put
                }
                val user = call.receive<UpdateUserRequest>()

                try {
                    userRepository.updateUser(id, user)
                } catch (e: NotFoundException) {
                    call.respond(HttpStatusCode.NotFound, "User not found")
                    return@put
                }
            }

            // Delete a user
            delete("{id}") {
                val id = call.parameters["id"]?.let { UUID.fromString(it) }
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid or missing ID")
                    return@delete
                }
                val deleted = userRepository.deleteUser(id)
                if (deleted) {
                    call.respond(HttpStatusCode.OK, "User deleted successfully")
                } else {
                    call.respond(HttpStatusCode.NotFound, "User not found")
                }
            }
        }
    }
}