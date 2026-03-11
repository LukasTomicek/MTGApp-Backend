package mtg.app.feature.matches.api

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import mtg.app.feature.matches.application.LoadMatchesUseCase
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

private val matchesLogger = LoggerFactory.getLogger("MatchesEndpoint")

fun Route.registerMatchRoutes(
    loadMatches: LoadMatchesUseCase,
) {
    route("/v1/matches") {
        get {
            val userId = call.request.queryParameters["userId"].orEmpty()
            matchesLogger.info("GET /v1/matches called userId='{}'", userId)

            val matches = loadMatches(userId = userId)

            matchesLogger.info(
                "GET /v1/matches completed userId='{}' resultCount={}",
                userId,
                matches.size,
            )

            call.respond(matches.map { it.toResponse() })
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
