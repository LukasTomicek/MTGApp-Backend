package mtg.app.feature.offers.application

import mtg.app.core.error.ValidationException
import mtg.app.feature.offers.domain.Offer
import mtg.app.feature.offers.domain.OfferRepository
import mtg.app.feature.offers.domain.OfferType
import kotlin.time.Clock

class CreateOfferUseCase(
    private val repository: OfferRepository,
) {
    suspend operator fun invoke(
        userId: String,
        cardId: String,
        cardName: String,
        cardTypeLine: String?,
        cardImageUrl: String?,
        type: OfferType,
        price: Double?,
    ): Offer {
        val normalizedUserId = userId.trim()
        val normalizedCardId = cardId.trim()
        val normalizedCardName = cardName.trim()
        val normalizedCardTypeLine = cardTypeLine?.trim()?.takeIf { it.isNotBlank() }
        val normalizedCardImageUrl = cardImageUrl?.trim()?.takeIf { it.isNotBlank() }

        if (normalizedUserId.isBlank()) throw ValidationException("userId is required")
        if (normalizedCardId.isBlank()) throw ValidationException("cardId is required")
        if (normalizedCardName.isBlank()) throw ValidationException("cardName is required")
        if (price != null && price < 0.0) throw ValidationException("price must be >= 0")

        val now = Clock.System.now().toEpochMilliseconds()
        val id = "offer_${java.util.UUID.randomUUID()}"

        val offer = Offer(
            id = id,
            userId = normalizedUserId,
            cardId = normalizedCardId,
            cardName = normalizedCardName,
            cardTypeLine = normalizedCardTypeLine,
            cardImageUrl = normalizedCardImageUrl,
            type = type,
            price = price,
            createdAt = now,
        )

        return repository.create(offer)
    }
}
