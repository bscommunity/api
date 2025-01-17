package org.bscm.routes

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.bscm.models.User
import org.bscm.models.dto.CreateUserRequest
import org.bscm.repository.UserRepository
import java.util.*

fun Route.userRoutes(userRepository: UserRepository) {
    route("/users") {
        // Get all users
        get {
            val users = userRepository.getAllUsers()
            call.respond(users)
        }

        // Get user by ID
        get("{id}") {
            val id = call.parameters["id"]?.let { UUID.fromString(it) }
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid or missing ID")
                return@get
            }

            val user = userRepository.getUserById(id)
            if (user != null) {
                call.respond(user)
            } else {
                call.respond(HttpStatusCode.NotFound, "User not found")
            }
        }

        // Create a new user
        post {
            val createRequest = call.receive<CreateUserRequest>()

            // Create a full 'User' object from the request
            val user = User.create(createRequest)

            val createdUser = userRepository.createUser(user)
            call.respond(HttpStatusCode.Created, createdUser)
        }

        // Update an existing user
        put("{id}") {
            val id = call.parameters["id"]?.let { UUID.fromString(it) }
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid or missing ID")
                return@put
            }
            val user = call.receive<CreateUserRequest>()
            val updated = userRepository.updateUser(id, user)
            if (updated) {
                call.respond(HttpStatusCode.OK, "User updated successfully")
            } else {
                call.respond(HttpStatusCode.NotFound, "User not found")
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