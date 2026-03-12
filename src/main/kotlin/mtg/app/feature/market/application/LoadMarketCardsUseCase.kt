package mtg.app.feature.market.application

import mtg.app.feature.market.domain.MarketCardSummary
import mtg.app.feature.offers.domain.Offer
import mtg.app.feature.offers.domain.OfferRepository
import mtg.app.feature.offers.domain.OfferType

class LoadMarketCardsUseCase(
    private val offerRepository: OfferRepository,
    private val visibilitySupport: MarketVisibilitySupport,
) {
    suspend operator fun invoke(
        viewerUid: String,
        query: String?,
        offerType: OfferType,
        limit: Int?,
    ): List<MarketCardSummary> {
        val normalizedQuery = query?.trim().orEmpty().ifBlank { null }?.lowercase()
        val normalizedLimit = limit?.coerceAtLeast(0)

        val visibleOffers = visibilitySupport.visibleOffersForViewer(
            viewerUid = viewerUid,
            offerType = offerType,
            offers = offerRepository.list(
                cardId = null,
                userId = null,
                type = offerType,
            ),
        )

        val filtered = if (normalizedQuery == null) {
            visibleOffers
        } else {
            visibleOffers.filter { offer ->
                offer.cardName.lowercase().contains(normalizedQuery)
            }
        }

        val grouped = filtered
            .groupBy { offer -> offer.marketKey() }
            .mapNotNull { (_, offers) -> offers.toCardSummary(offerType) }

        val sorted = if (normalizedQuery == null) {
            grouped.sortedByDescending { it.latestCreatedAt }
        } else {
            grouped.sortedBy { it.cardName.lowercase() }
        }

        return sorted
            .let { if (normalizedLimit != null) it.take(normalizedLimit) else it }
            .map { it.toPublicSummary() }
    }
}

private data class InternalMarketCardSummary(
    val cardId: String,
    val cardName: String,
    val cardTypeLine: String?,
    val imageUrl: String?,
    val offerCount: Int,
    val fromPrice: Double?,
    val latestCreatedAt: Long,
)

private fun List<Offer>.toCardSummary(offerType: OfferType): InternalMarketCardSummary? {
    val first = firstOrNull() ?: return null
    return InternalMarketCardSummary(
        cardId = first.cardId.ifBlank { first.cardName },
        cardName = first.cardName,
        cardTypeLine = first.cardTypeLine?.trim()?.takeIf { it.isNotBlank() },
        imageUrl = firstNotNullOfOrNull { it.cardImageUrl?.trim()?.takeIf { url -> url.isNotBlank() } },
        offerCount = size,
        fromPrice = when (offerType) {
            OfferType.SELL -> mapNotNull { it.price }.minOrNull()
            OfferType.BUY -> mapNotNull { it.price }.maxOrNull()
        },
        latestCreatedAt = maxOfOrNull { it.createdAt } ?: 0L,
    )
}

private fun InternalMarketCardSummary.toPublicSummary(): MarketCardSummary {
    return MarketCardSummary(
        cardId = cardId,
        cardName = cardName,
        cardTypeLine = cardTypeLine,
        imageUrl = imageUrl,
        offerCount = offerCount,
        fromPrice = fromPrice,
    )
}

private fun Offer.marketKey(): String {
    return if (cardId.isNotBlank()) {
        "id:${cardId.trim().lowercase()}"
    } else {
        "name:${cardName.trim().lowercase()}"
    }
}
