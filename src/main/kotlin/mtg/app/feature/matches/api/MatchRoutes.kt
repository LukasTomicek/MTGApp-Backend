package mtg.app.feature.matches.api

import io.ktor.server.application.call
import io.ktor.server.request.receiveNullable
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import mtg.app.core.auth.FirebaseAuthVerifier
import mtg.app.core.auth.requireFirebasePrincipal
import mtg.app.feature.matches.application.LoadMatchesUseCase
import mtg.app.feature.matches.application.SyncMatchNotificationsUseCase
import mtg.app.feature.matches.domain.MatchSummary
import org.slf4j.LoggerFactory

@Serializable
private data class MatchResponse(
    val cardId: String,
    val cardName: String,
    val myType: String,
    val counterpartType: String,
    val counterpartUserId: String,
    val counterpartDisplayName: String,
    val myOfferCount: Int,
    val counterpartOfferCount: Int,
    val myBestPrice: Double?,
    val counterpartBestPrice: Double?,
)

@Serializable
private data class SyncMatchesRequest(
    val type: String? = null,
)

@Serializable
private data class SyncMatchesResponse(
    val syncedNotifications: Int,
)

private val matchesLogger = LoggerFactory.getLogger("MatchesEndpoint")

fun Route.registerMatchRoutes(
    authVerifier: FirebaseAuthVerifier,
    loadMatches: LoadMatchesUseCase,
    syncMatchNotifications: SyncMatchNotificationsUseCase,
) {
    route("/v1/matches") {
        get {
            val principal = call.requireFirebasePrincipal(authVerifier)
            val userId = principal.uid
            matchesLogger.info("GET /v1/matches called userId='{}'", userId)

            val matches = loadMatches(userId = userId)

            matchesLogger.info(
                "GET /v1/matches completed userId='{}' resultCount={}",
                userId,
                matches.size,
            )

            call.respond(matches.map { it.toResponse() })
        }

        post("/sync") {
            val principal = call.requireFirebasePrincipal(authVerifier)
            val request = call.receiveNullable<SyncMatchesRequest>()
            val type = SyncMatchNotificationsUseCase.MatchSyncType.fromRaw(request?.type)
            matchesLogger.info("POST /v1/matches/sync called userId='{}' type='{}'", principal.uid, type)
            val syncedNotifications = syncMatchNotifications(
                userId = principal.uid,
                type = type,
            )
            call.respond(SyncMatchesResponse(syncedNotifications = syncedNotifications))
        }
    }
}

private fun MatchSummary.toResponse(): MatchResponse {
    return MatchResponse(
        cardId = cardId,
        cardName = cardName,
        myType = myType.name,
        counterpartType = counterpartType.name,
        counterpartUserId = counterpartUserId,
        counterpartDisplayName = counterpartDisplayName,
        myOfferCount = myOfferCount,
        counterpartOfferCount = counterpartOfferCount,
        myBestPrice = myBestPrice,
        counterpartBestPrice = counterpartBestPrice,
    )
}
