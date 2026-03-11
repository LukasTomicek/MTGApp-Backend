package mtg.app.feature.matches.domain

import mtg.app.feature.offers.domain.OfferType

data class MatchSummary(
    val cardId: String,
    val cardName: String,
    val myType: OfferType,
    val counterpartType: OfferType,
    val counterpartUserId: String,
    val counterpartDisplayName: String,
    val myOfferCount: Int,
    val counterpartOfferCount: Int,
    val myBestPrice: Double?,
    val counterpartBestPrice: Double?,
)
