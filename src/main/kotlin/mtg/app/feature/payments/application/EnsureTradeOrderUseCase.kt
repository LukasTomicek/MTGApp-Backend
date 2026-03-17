package mtg.app.feature.payments.application

import mtg.app.core.error.ValidationException
import mtg.app.feature.offers.domain.OfferRepository
import mtg.app.feature.offers.domain.OfferType
import mtg.app.feature.payments.domain.PaymentStatus
import mtg.app.feature.payments.domain.PaymentsRepository
import mtg.app.feature.payments.domain.PayoutStatus
import mtg.app.feature.payments.domain.TradeOrder
import java.util.UUID
import kotlin.math.roundToLong

class EnsureTradeOrderUseCase(
    private val repository: PaymentsRepository,
    private val offerRepository: OfferRepository,
    private val defaultCurrency: String,
    private val feePercent: Double,
) {
    suspend operator fun invoke(
        chatId: String,
        cardId: String,
        cardName: String,
        buyerUserId: String,
        sellerUserId: String,
    ): TradeOrder {
        repository.findOrderByChatId(chatId)?.let { return it }

        val pricedOffer = offerRepository.list(cardId = cardId, userId = sellerUserId, type = OfferType.SELL)
            .filter { it.price != null }
            .minByOrNull { it.price ?: Double.MAX_VALUE }
            ?: throw ValidationException("Seller has no priced offer for this card")

        val amountMinor = ((pricedOffer.price ?: 0.0) * 100.0).roundToLong()
        if (amountMinor <= 0L) throw ValidationException("Order amount must be greater than zero")

        val feeMinor = (amountMinor * feePercent / 100.0).roundToLong().coerceAtLeast(0L)
        val sellerAmountMinor = (amountMinor - feeMinor).coerceAtLeast(0L)
        val now = System.currentTimeMillis()

        return repository.saveOrder(
            TradeOrder(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                cardId = cardId,
                cardName = cardName,
                buyerUserId = buyerUserId,
                sellerUserId = sellerUserId,
                amountMinor = amountMinor,
                currency = defaultCurrency,
                platformFeeMinor = feeMinor,
                sellerAmountMinor = sellerAmountMinor,
                paymentStatus = PaymentStatus.PENDING,
                payoutStatus = PayoutStatus.NOT_READY,
                createdAt = now,
                updatedAt = now,
            )
        )
    }
}
