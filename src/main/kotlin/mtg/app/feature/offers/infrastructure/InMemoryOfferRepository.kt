package mtg.app.feature.offers.infrastructure

import mtg.app.feature.offers.domain.Offer
import mtg.app.feature.offers.domain.OfferRepository
import mtg.app.feature.offers.domain.OfferType

class InMemoryOfferRepository : OfferRepository {
    private val offers = linkedMapOf<String, Offer>()

    override suspend fun create(offer: Offer): Offer {
        offers[offer.id] = offer
        return offer
    }

    override suspend fun list(cardId: String?, userId: String?, type: OfferType?): List<Offer> {
        return offers.values
            .asSequence()
            .filter { offer -> cardId == null || offer.cardId == cardId }
            .filter { offer -> userId == null || offer.userId == userId }
            .filter { offer -> type == null || offer.type == type }
            .sortedByDescending { it.createdAt }
            .toList()
    }

    override suspend fun deleteOwned(id: String, ownerUserId: String): Boolean {
        val offer = offers[id] ?: return false
        if (offer.userId != ownerUserId) return false
        return offers.remove(id) != null
    }
}
