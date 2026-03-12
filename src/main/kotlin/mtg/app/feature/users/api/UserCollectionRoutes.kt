package mtg.app.feature.users.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.json.JsonObject
import mtg.app.core.auth.FirebaseAuthVerifier
import mtg.app.core.auth.requireFirebasePrincipal
import mtg.app.feature.bridge.infrastructure.PostgresBridgeRepository

fun Route.registerUserCollectionRoutes(
    authVerifier: FirebaseAuthVerifier,
    bridgeRepository: PostgresBridgeRepository,
) {
    route("/v1/users/me/collection") {
        get {
            val principal = call.requireFirebasePrincipal(authVerifier)
            call.respond(bridgeRepository.listUserCollection(principal.uid))
        }

        put("/{entryId}") {
            val principal = call.requireFirebasePrincipal(authVerifier)
            val entryId = call.parameters["entryId"].orEmpty()
            val payload = call.receive<JsonObject>()
            bridgeRepository.upsertUserCollectionEntry(
                uid = principal.uid,
                entryId = entryId,
                payload = payload,
            )
            call.respond(HttpStatusCode.OK)
        }

        delete("/{entryId}") {
            val principal = call.requireFirebasePrincipal(authVerifier)
            val entryId = call.parameters["entryId"].orEmpty()
            bridgeRepository.deleteUserCollectionEntry(principal.uid, entryId)
            call.respond(HttpStatusCode.OK)
        }

        delete {
            val principal = call.requireFirebasePrincipal(authVerifier)
            bridgeRepository.clearUserCollection(principal.uid)
            call.respond(HttpStatusCode.OK)
        }
    }
}
