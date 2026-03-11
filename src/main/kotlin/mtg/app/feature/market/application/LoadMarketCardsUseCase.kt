package mtg.app.feature.market.application

import mtg.app.feature.market.domain.MarketCardSummary
import mtg.app.feature.offers.domain.OfferRepository
import mtg.app.feature.offers.domain.OfferType

class LoadMarketCardsUseCase(
    private val offerRepository: OfferRepository,
) {
    suspend operator fun invoke(
        query: String?,
        excludeUserId: String?,
    ): List<MarketCardSummary> {
        val normalizedQuery = query?.trim().orEmpty().ifBlank { null }?.lowercase()
        val normalizedExcludedUserId = excludeUserId?.trim().orEmpty().ifBlank { null }

        val sellOffers = offerRepository.list(
            cardId = null,
            userId = null,
            type = OfferType.SELL,
        ).filter { offer ->
            normalizedExcludedUserId == null || offer.userId != normalizedExcludedUserId
        }

        val filtered = if (normalizedQuery == null) {
            sellOffers
        } else {
            sellOffers.filter { offer ->
                offer.cardName.lowercase().contains(normalizedQuery)
            }
        }

        return filtered
            .groupBy { offer ->
                offer.cardId.ifBlank { offer.cardName.trim().lowercase() }
            }
            .mapNotNull { (_, offers) ->
                val first = offers.firstOrNull() ?: return@mapNotNull null
                MarketCardSummary(
                    cardId = first.cardId.ifBlank { first.cardName },
                    cardName = first.cardName,
                    imageUrl = offers.firstNotNullOfOrNull { it.cardImageUrl?.trim()?.takeIf { url -> url.isNotBlank() } },
                    offerCount = offers.size,
                    fromPrice = offers.mapNotNull { it.price }.minOrNull(),
                )
            }
            .sortedBy { it.cardName.lowercase() }
    }
}
