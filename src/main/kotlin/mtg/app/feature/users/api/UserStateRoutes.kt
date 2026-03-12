package mtg.app.feature.users.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mtg.app.core.auth.FirebaseAuthVerifier
import mtg.app.core.auth.requireFirebasePrincipal
import mtg.app.feature.bridge.infrastructure.PostgresBridgeRepository
import mtg.app.feature.bridge.infrastructure.PostgresRatingStore

@Serializable
private data class UpdateOnboardingRequest(
    val completed: Boolean,
)

fun Route.registerUserStateRoutes(
    authVerifier: FirebaseAuthVerifier,
    bridgeRepository: PostgresBridgeRepository,
    ratingStore: PostgresRatingStore,
) {
    route("/v1/users") {
        route("/me") {
            get("/matches") {
                val principal = call.requireFirebasePrincipal(authVerifier)
                call.respond(bridgeRepository.listUserMatches(principal.uid))
            }

            get("/onboarding") {
                val principal = call.requireFirebasePrincipal(authVerifier)
                call.respond(
                    buildJsonObject {
                        put("completed", bridgeRepository.loadOnboardingCompleted(principal.uid))
                    }
                )
            }

            put("/onboarding") {
                val principal = call.requireFirebasePrincipal(authVerifier)
                val request = call.receive<UpdateOnboardingRequest>()
                bridgeRepository.updateOnboardingCompleted(principal.uid, request.completed)
                call.respond(HttpStatusCode.OK)
            }

            get("/ratings/given/{chatId}") {
                val principal = call.requireFirebasePrincipal(authVerifier)
                val chatId = call.parameters["chatId"].orEmpty()
                call.respond(
                    buildJsonObject {
                        put("exists", ratingStore.hasRatedChat(principal.uid, chatId))
                    }
                )
            }
        }

        get("/{uid}/ratings") {
            call.requireFirebasePrincipal(authVerifier)
            val uid = call.parameters["uid"].orEmpty()
            call.respond(ratingStore.listReceivedRatings(uid))
        }
    }
}
