package mtg.app.feature.market.domain

data class MarketCardSummary(
    val cardId: String,
    val cardName: String,
    val imageUrl: String?,
    val offerCount: Int,
    val fromPrice: Double?,
)

data class MarketSellerSummary(
    val userId: String,
    val displayName: String,
    val offerCount: Int,
    val fromPrice: Double?,
)
