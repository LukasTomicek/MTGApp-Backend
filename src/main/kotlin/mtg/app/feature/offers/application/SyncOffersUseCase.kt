package mtg.app.feature.offers.application

import mtg.app.core.error.ValidationException
import mtg.app.feature.offers.domain.Offer
import mtg.app.feature.offers.domain.OfferRepository
import mtg.app.feature.offers.domain.OfferType
import kotlin.time.Clock

class SyncOffersUseCase(
    private val repository: OfferRepository,
) {
    suspend operator fun invoke(
        ownerUserId: String,
        type: OfferType,
        desiredEntries: List<DesiredOfferEntry>,
    ): SyncOffersResult {
        val normalizedOwnerUserId = ownerUserId.trim()
        if (normalizedOwnerUserId.isBlank()) throw ValidationException("ownerUserId is required")

        val normalizedDesired = desiredEntries.map { normalize(it, type = type, ownerUserId = normalizedOwnerUserId) }
        val existingOffers = repository.list(cardId = null, userId = normalizedOwnerUserId, type = type)

        val remainingDesired = normalizedDesired.toMutableList()
        val offersToDelete = mutableListOf<Offer>()

        existingOffers.forEach { existing ->
            val existingComparable = ComparableOffer.from(existing)
            val matchIndex = remainingDesired.indexOfFirst { it == existingComparable }
            if (matchIndex >= 0) {
                remainingDesired.removeAt(matchIndex)
            } else {
                offersToDelete += existing
            }
        }

        offersToDelete.forEach { repository.deleteOwned(it.id, normalizedOwnerUserId) }

        val now = Clock.System.now().toEpochMilliseconds()
        remainingDesired.forEachIndexed { index, desired ->
            repository.create(
                Offer(
                    id = "offer_${java.util.UUID.randomUUID()}",
                    userId = normalizedOwnerUserId,
                    cardId = desired.cardId,
                    cardName = desired.cardName,
                    cardTypeLine = desired.cardTypeLine,
                    cardImageUrl = desired.cardImageUrl,
                    type = type,
                    price = desired.price,
                    createdAt = now + index,
                )
            )
        }

        return SyncOffersResult(
            deletedCount = offersToDelete.size,
            createdCount = remainingDesired.size,
            unchangedCount = existingOffers.size - offersToDelete.size,
        )
    }

    data class DesiredOfferEntry(
        val cardId: String,
        val cardName: String,
        val cardTypeLine: String? = null,
        val cardImageUrl: String? = null,
        val price: Double? = null,
    )

    data class SyncOffersResult(
        val deletedCount: Int,
        val createdCount: Int,
        val unchangedCount: Int,
    )

    private fun normalize(entry: DesiredOfferEntry, type: OfferType, ownerUserId: String): ComparableOffer {
        val normalizedCardId = entry.cardId.trim()
        val normalizedCardName = entry.cardName.trim()
        val normalizedCardTypeLine = entry.cardTypeLine?.trim()?.takeIf { it.isNotBlank() }
        val normalizedCardImageUrl = entry.cardImageUrl?.trim()?.takeIf { it.isNotBlank() }
        val normalizedPrice = entry.price?.also {
            if (it < 0.0) throw ValidationException("price must be >= 0")
        }
        if (normalizedCardId.isBlank()) throw ValidationException("cardId is required")
        if (normalizedCardName.isBlank()) throw ValidationException("cardName is required")
        return ComparableOffer(
            userId = ownerUserId,
            cardId = normalizedCardId,
            cardName = normalizedCardName,
            cardTypeLine = normalizedCardTypeLine,
            cardImageUrl = normalizedCardImageUrl,
            type = type,
            price = normalizedPrice,
        )
    }

    private data class ComparableOffer(
        val userId: String,
        val cardId: String,
        val cardName: String,
        val cardTypeLine: String?,
        val cardImageUrl: String?,
        val type: OfferType,
        val price: Double?,
    ) {
        companion object {
            fun from(offer: Offer): ComparableOffer {
                return ComparableOffer(
                    userId = offer.userId,
                    cardId = offer.cardId.trim(),
                    cardName = offer.cardName.trim(),
                    cardTypeLine = offer.cardTypeLine?.trim()?.takeIf { it.isNotBlank() },
                    cardImageUrl = offer.cardImageUrl?.trim()?.takeIf { it.isNotBlank() },
                    type = offer.type,
                    price = offer.price,
                )
            }
        }
    }
}
