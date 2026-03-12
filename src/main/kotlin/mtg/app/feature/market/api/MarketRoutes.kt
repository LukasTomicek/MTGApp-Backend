package mtg.app.feature.market.api

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import mtg.app.core.auth.FirebaseAuthVerifier
import mtg.app.core.auth.requireFirebasePrincipal
import mtg.app.core.error.ValidationException
import mtg.app.feature.market.application.LoadMarketCardsUseCase
import mtg.app.feature.market.application.LoadMarketSellersUseCase
import mtg.app.feature.market.domain.MarketCardSummary
import mtg.app.feature.market.domain.MarketSellerSummary
import mtg.app.feature.offers.domain.OfferType

@Serializable
private data class MarketCardResponse(
    val cardId: String,
    val cardName: String,
    val cardTypeLine: String?,
    val imageUrl: String?,
    val offerCount: Int,
    val fromPrice: Double?,
)

@Serializable
private data class MarketSellerResponse(
    val userId: String,
    val displayName: String,
    val offerCount: Int,
    val fromPrice: Double?,
)

fun Route.registerMarketRoutes(
    authVerifier: FirebaseAuthVerifier,
    loadMarketCards: LoadMarketCardsUseCase,
    loadMarketSellers: LoadMarketSellersUseCase,
) {
    route("/v1/market") {
        get("/cards") {
            val principal = call.requireFirebasePrincipal(authVerifier)
            val query = call.request.queryParameters["query"]
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()
            val offerType = call.request.queryParameters["type"]
                ?.trim()
                ?.uppercase()
                ?.takeIf { it.isNotBlank() }
                ?.let { raw ->
                    runCatching { OfferType.valueOf(raw) }
                        .getOrElse { throw ValidationException("Unsupported market type: $raw") }
                }
                ?: OfferType.SELL
            call.respond(
                loadMarketCards(
                    viewerUid = principal.uid,
                    query = query,
                    offerType = offerType,
                    limit = limit,
                ).map { it.toResponse() }
            )
        }

        get("/sellers") {
            val principal = call.requireFirebasePrincipal(authVerifier)
            val cardId = call.request.queryParameters["cardId"]
            val cardName = call.request.queryParameters["cardName"]
            call.respond(
                loadMarketSellers(
                    viewerUid = principal.uid,
                    cardId = cardId,
                    cardName = cardName,
                ).map { it.toResponse() }
            )
        }
    }
}

private fun MarketCardSummary.toResponse(): MarketCardResponse {
    return MarketCardResponse(
        cardId = cardId,
        cardName = cardName,
        cardTypeLine = cardTypeLine,
        imageUrl = imageUrl,
        offerCount = offerCount,
        fromPrice = fromPrice,
    )
}

private fun MarketSellerSummary.toResponse(): MarketSellerResponse {
    return MarketSellerResponse(
        userId = userId,
        displayName = displayName,
        offerCount = offerCount,
        fromPrice = fromPrice,
    )
}
