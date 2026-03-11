package mtg.app.feature.offers.domain

enum class OfferType {
    BUY,
    SELL,
}

data class Offer(
    val id: String,
    val userId: String,
    val cardId: String,
    val cardName: String,
    val cardTypeLine: String?,
    val cardImageUrl: String?,
    val type: OfferType,
    val price: Double?,
    val createdAt: Long,
)
