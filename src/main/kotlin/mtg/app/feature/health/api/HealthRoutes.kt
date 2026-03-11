package mtg.app.feature.health.api

import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import mtg.app.feature.health.application.GetHealthStatusUseCase
import mtg.app.feature.health.domain.HealthStatus
import kotlinx.serialization.Serializable

@Serializable
private data class HealthResponse(
    val status: String,
    val service: String,
)

fun Route.registerHealthRoutes(
    getHealthStatus: GetHealthStatusUseCase,
) {
    get("/") {
        call.respondText("mtg-backend is running")
    }

    get("/health") {
        call.respond(getHealthStatus().toResponse())
    }
}

private fun HealthStatus.toResponse(): HealthResponse {
    return HealthResponse(
        status = status,
        service = service,
    )
}
