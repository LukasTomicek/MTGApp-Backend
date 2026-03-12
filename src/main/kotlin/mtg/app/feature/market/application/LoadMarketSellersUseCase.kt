package mtg.app.feature.market.application

import mtg.app.core.error.ValidationException
import mtg.app.feature.market.domain.MarketSellerSummary
import mtg.app.feature.offers.domain.OfferRepository
import mtg.app.feature.offers.domain.OfferType
import mtg.app.feature.users.application.LoadNicknamesUseCase

class LoadMarketSellersUseCase(
    private val offerRepository: OfferRepository,
    private val loadNicknames: LoadNicknamesUseCase,
    private val visibilitySupport: MarketVisibilitySupport,
) {
    suspend operator fun invoke(
        viewerUid: String,
        cardId: String?,
        cardName: String?,
    ): List<MarketSellerSummary> {
        val normalizedCardId = cardId?.trim().orEmpty().ifBlank { null }
        val normalizedCardName = cardName?.trim().orEmpty().ifBlank { null }

        if (normalizedCardId == null && normalizedCardName == null) {
            throw ValidationException("cardId or cardName is required")
        }

        val visibleSellOffers = visibilitySupport.visibleOffersForViewer(
            viewerUid = viewerUid,
            offerType = OfferType.SELL,
            offers = offerRepository.list(
                cardId = null,
                userId = null,
                type = OfferType.SELL,
            ),
        )

        val matchedOffers = visibleSellOffers.filter { offer ->
            when {
                normalizedCardId != null -> offer.cardId.trim() == normalizedCardId
                else -> offer.cardName.trim().equals(normalizedCardName, ignoreCase = true)
            }
        }

        val offersByUser = matchedOffers.groupBy { it.userId }
        val nicknames = loadNicknames(offersByUser.keys)

        return offersByUser
            .map { (userId, offers) ->
                MarketSellerSummary(
                    userId = userId,
                    displayName = nicknames[userId]?.trim().orEmpty().ifBlank { userId },
                    offerCount = offers.size,
                    fromPrice = offers.mapNotNull { it.price }.minOrNull(),
                )
            }
            .sortedBy { it.fromPrice ?: Double.MAX_VALUE }
    }
}
