package org.bscm.routes

import io.ktor.http.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.bscm.models.dto.CreateChartRequest
import org.bscm.models.dto.UpdateChartRequest
import org.bscm.repository.ChartRepository
import java.util.*

fun Route.chartRoutes(chartRepository: ChartRepository) {
    route("/charts") {
        // Get all charts
        get {
            val charts = chartRepository.getAllCharts()
            call.respond(charts)
        }

        // Get chart by ID
        get("{id}") {
            val id = call.parameters["id"]?.let { UUID.fromString(it) }
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid or missing ID")
                return@get
            }

            val chart = chartRepository.getChartById(id)
            if (chart != null) {
                call.respond(chart)
            } else {
                call.respond(HttpStatusCode.NotFound, "Chart not found")
            }
        }

        // Create a new chart
        post {
            val createRequest = call.receive<CreateChartRequest>()
            val createdChart = chartRepository.createChart(createRequest)
            call.respond(HttpStatusCode.Created, createdChart)
        }

        // Update an existing chart
        put("{id}") {
            val id = call.parameters["id"]?.let { UUID.fromString(it) }
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid or missing ID")
                return@put
            }

            val updateRequest = call.receive<UpdateChartRequest>()

            try {
                val updatedChart = chartRepository.updateChart(id, updateRequest)
                call.respond(updatedChart)
            } catch (e: NotFoundException) {
                call.respond(HttpStatusCode.NotFound, e.message ?: "Not Found")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, e.message ?: "Internal Server Error")
            }
        }

        // Delete a chart
        delete("{id}") {
            val id = call.parameters["id"]?.let { UUID.fromString(it) }
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid or missing ID")
                return@delete
            }

            val deleted = chartRepository.deleteChart(id)
            if (deleted) {
                call.respond(HttpStatusCode.OK, "Chart deleted successfully")
            } else {
                call.respond(HttpStatusCode.NotFound, "Chart not found")
            }
        }
    }
}
