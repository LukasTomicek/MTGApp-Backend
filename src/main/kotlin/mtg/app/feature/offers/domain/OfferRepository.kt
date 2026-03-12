package mtg.app.feature.offers.domain

interface OfferRepository {
    suspend fun create(offer: Offer): Offer
    suspend fun list(cardId: String?, userId: String?, type: OfferType?): List<Offer>
    suspend fun deleteOwned(id: String, ownerUserId: String): Boolean
}
