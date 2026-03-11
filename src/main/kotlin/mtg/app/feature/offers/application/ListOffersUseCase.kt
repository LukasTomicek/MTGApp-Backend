package mtg.app.feature.offers.application

import mtg.app.feature.offers.domain.Offer
import mtg.app.feature.offers.domain.OfferRepository
import mtg.app.feature.offers.domain.OfferType

class ListOffersUseCase(
    private val repository: OfferRepository,
) {
    suspend operator fun invoke(
        cardId: String? = null,
        userId: String? = null,
        type: OfferType? = null,
    ): List<Offer> {
        val normalizedCardId = cardId?.trim().orEmpty().ifBlank { null }
        val normalizedUserId = userId?.trim().orEmpty().ifBlank { null }
        return repository.list(
            cardId = normalizedCardId,
            userId = normalizedUserId,
            type = type,
        )
    }
}
