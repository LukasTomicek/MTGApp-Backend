package mtg.app.feature.users.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.json.JsonObject
import mtg.app.core.auth.FirebaseAuthVerifier
import mtg.app.core.auth.requireFirebasePrincipal
import mtg.app.feature.bridge.infrastructure.PostgresBridgeRepository

fun Route.registerUserMapPinRoutes(
    authVerifier: FirebaseAuthVerifier,
    bridgeRepository: PostgresBridgeRepository,
) {
    route("/v1/users/me/map-pins") {
        get {
            val principal = call.requireFirebasePrincipal(authVerifier)
            call.respond(bridgeRepository.listUserMapPins(principal.uid))
        }

        put {
            val principal = call.requireFirebasePrincipal(authVerifier)
            val payload = call.receive<JsonObject>()
            bridgeRepository.replaceUserMapPins(principal.uid, payload)
            call.respond(HttpStatusCode.OK)
        }
    }

    route("/v1/market/map-pins") {
        get {
            call.requireFirebasePrincipal(authVerifier)
            call.respond(bridgeRepository.listMarketplaceMapPinsByUser())
        }
    }
}
