package mtg.app.feature.market.application

import mtg.app.core.error.ValidationException
import mtg.app.feature.market.domain.MarketSellerSummary
import mtg.app.feature.offers.domain.OfferRepository
import mtg.app.feature.offers.domain.OfferType
import mtg.app.feature.users.application.LoadNicknamesUseCase

class LoadMarketSellersUseCase(
    private val offerRepository: OfferRepository,
    private val loadNicknames: LoadNicknamesUseCase,
) {
    suspend operator fun invoke(
        cardId: String?,
        cardName: String?,
        excludeUserId: String?,
    ): List<MarketSellerSummary> {
        val normalizedCardId = cardId?.trim().orEmpty().ifBlank { null }
        val normalizedCardName = cardName?.trim().orEmpty().ifBlank { null }
        val normalizedExcludedUserId = excludeUserId?.trim().orEmpty().ifBlank { null }

        if (normalizedCardId == null && normalizedCardName == null) {
            throw ValidationException("cardId or cardName is required")
        }

        val sellOffers = offerRepository.list(
            cardId = null,
            userId = null,
            type = OfferType.SELL,
        )

        val matchedOffers = sellOffers.filter { offer ->
            val cardMatches = when {
                normalizedCardId != null -> offer.cardId.trim() == normalizedCardId
                else -> offer.cardName.trim().equals(normalizedCardName, ignoreCase = true)
            }
            val userMatches = normalizedExcludedUserId == null || offer.userId != normalizedExcludedUserId
            cardMatches && userMatches
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
            .sortedBy { it.displayName.lowercase() }
    }
}
