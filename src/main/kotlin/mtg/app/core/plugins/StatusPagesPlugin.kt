package mtg.app.core.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import mtg.app.core.error.AppException
import mtg.app.core.error.ErrorResponse
import mtg.app.core.error.ForbiddenException
import mtg.app.core.error.UnauthorizedException

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<UnauthorizedException> { call, cause ->
            call.respond(
                status = HttpStatusCode.Unauthorized,
                message = ErrorResponse(cause.message ?: "Unauthorized"),
            )
        }

        exception<ForbiddenException> { call, cause ->
            call.respond(
                status = HttpStatusCode.Forbidden,
                message = ErrorResponse(cause.message ?: "Forbidden"),
            )
        }

        exception<AppException> { call, cause ->
            call.respond(
                status = HttpStatusCode.BadRequest,
                message = ErrorResponse(cause.message ?: "Request failed"),
            )
        }

        exception<Throwable> { call, cause ->
            call.respond(
                status = HttpStatusCode.InternalServerError,
                message = ErrorResponse(cause.message ?: "Unexpected server error"),
            )
        }
    }
}
