package mtg.app.feature.market.api

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import mtg.app.feature.market.application.LoadMarketCardsUseCase
import mtg.app.feature.market.application.LoadMarketSellersUseCase
import mtg.app.feature.market.domain.MarketCardSummary
import mtg.app.feature.market.domain.MarketSellerSummary

@Serializable
private data class MarketCardResponse(
    val cardId: String,
    val cardName: String,
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
    loadMarketCards: LoadMarketCardsUseCase,
    loadMarketSellers: LoadMarketSellersUseCase,
) {
    route("/v1/market") {
        get("/cards") {
            val query = call.request.queryParameters["query"]
            val excludeUserId = call.request.queryParameters["excludeUserId"]
            call.respond(
                loadMarketCards(
                    query = query,
                    excludeUserId = excludeUserId,
                ).map { it.toResponse() }
            )
        }

        get("/sellers") {
            val cardId = call.request.queryParameters["cardId"]
            val cardName = call.request.queryParameters["cardName"]
            val excludeUserId = call.request.queryParameters["excludeUserId"]
            call.respond(
                loadMarketSellers(
                    cardId = cardId,
                    cardName = cardName,
                    excludeUserId = excludeUserId,
                ).map { it.toResponse() }
            )
        }
    }
}

private fun MarketCardSummary.toResponse(): MarketCardResponse {
    return MarketCardResponse(
        cardId = cardId,
        cardName = cardName,
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
