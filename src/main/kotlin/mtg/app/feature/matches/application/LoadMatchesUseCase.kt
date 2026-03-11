package mtg.app.feature.matches.application

import mtg.app.core.error.ValidationException
import mtg.app.feature.matches.domain.MatchSummary
import mtg.app.feature.offers.domain.Offer
import mtg.app.feature.offers.domain.OfferRepository
import mtg.app.feature.offers.domain.OfferType
import mtg.app.feature.users.application.LoadNicknamesUseCase

class LoadMatchesUseCase(
    private val offerRepository: OfferRepository,
    private val loadNicknames: LoadNicknamesUseCase,
) {
    suspend operator fun invoke(userId: String): List<MatchSummary> {
        val normalizedUserId = userId.trim()
        if (normalizedUserId.isBlank()) {
            throw ValidationException("userId is required")
        }

        val allOffers = offerRepository.list(
            cardId = null,
            userId = null,
            type = null,
        )

        val myOffers = allOffers.filter { it.userId == normalizedUserId }
        if (myOffers.isEmpty()) return emptyList()

        val offersByCardAndType = allOffers.groupBy { it.cardId to it.type }
        val summaryByKey = linkedMapOf<SummaryKey, SummaryAccumulator>()

        myOffers.groupBy { it.cardId to it.type }.forEach { (cardAndType, groupedMyOffers) ->
            val (cardId, myType) = cardAndType
            val counterpartType = if (myType == OfferType.BUY) OfferType.SELL else OfferType.BUY
            val counterpartOffers = offersByCardAndType[cardId to counterpartType]
                .orEmpty()
                .filter { it.userId != normalizedUserId }

            if (counterpartOffers.isEmpty()) return@forEach

            val counterpartByUser = counterpartOffers.groupBy { it.userId }
            counterpartByUser.forEach { (counterpartUserId, userOffers) ->
                val key = SummaryKey(
                    cardId = cardId,
                    myType = myType,
                    counterpartUserId = counterpartUserId,
                )
                summaryByKey[key] = SummaryAccumulator(
                    cardName = groupedMyOffers.first().cardName,
                    myType = myType,
                    counterpartType = counterpartType,
                    counterpartUserId = counterpartUserId,
                    myOfferCount = groupedMyOffers.size,
                    counterpartOfferCount = userOffers.size,
                    myBestPrice = bestPriceForType(groupedMyOffers, myType),
                    counterpartBestPrice = bestPriceForType(userOffers, counterpartType),
                )
            }
        }

        if (summaryByKey.isEmpty()) return emptyList()

        val nicknames = loadNicknames(summaryByKey.values.map { it.counterpartUserId }.toSet())

        return summaryByKey
            .map { (key, value) ->
                MatchSummary(
                    cardId = key.cardId,
                    cardName = value.cardName,
                    myType = value.myType,
                    counterpartType = value.counterpartType,
                    counterpartUserId = value.counterpartUserId,
                    counterpartDisplayName = nicknames[value.counterpartUserId]
                        ?.trim()
                        .orEmpty()
                        .ifBlank { value.counterpartUserId },
                    myOfferCount = value.myOfferCount,
                    counterpartOfferCount = value.counterpartOfferCount,
                    myBestPrice = value.myBestPrice,
                    counterpartBestPrice = value.counterpartBestPrice,
                )
            }
            .sortedWith(
                compareBy<MatchSummary> { it.cardName.lowercase() }
                    .thenBy { it.counterpartDisplayName.lowercase() }
                    .thenBy { it.counterpartUserId }
            )
    }

    private fun bestPriceForType(offers: List<Offer>, type: OfferType): Double? {
        val prices = offers.mapNotNull { it.price }
        if (prices.isEmpty()) return null
        return when (type) {
            OfferType.SELL -> prices.minOrNull()
            OfferType.BUY -> prices.maxOrNull()
        }
    }

    private data class SummaryKey(
        val cardId: String,
        val myType: OfferType,
        val counterpartUserId: String,
    )

    private data class SummaryAccumulator(
        val cardName: String,
        val myType: OfferType,
        val counterpartType: OfferType,
        val counterpartUserId: String,
        val myOfferCount: Int,
        val counterpartOfferCount: Int,
        val myBestPrice: Double?,
        val counterpartBestPrice: Double?,
    )
}
