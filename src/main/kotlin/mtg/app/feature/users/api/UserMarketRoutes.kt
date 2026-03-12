package mtg.app.feature.users.api

import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import mtg.app.core.auth.FirebaseAuthVerifier
import mtg.app.core.auth.requireFirebasePrincipal
import mtg.app.feature.bridge.infrastructure.PostgresBridgeRepository

fun Route.registerUserMarketRoutes(
    authVerifier: FirebaseAuthVerifier,
    bridgeRepository: PostgresBridgeRepository,
) {
    route("/v1/users/{uid}/sell-offers") {
        get {
            call.requireFirebasePrincipal(authVerifier)
            val uid = call.parameters["uid"].orEmpty()
            call.respond(bridgeRepository.listMarketplaceSellEntriesByUser(uid))
        }
    }
}
