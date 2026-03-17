package mtg.app.feature.payments.domain

enum class PaymentStatus {
    PENDING,
    PAID,
    FAILED,
    REFUNDED,
}

enum class PayoutStatus {
    NOT_READY,
    PAID_OUT,
    FAILED,
}

data class TradeOrder(
    val id: String,
    val chatId: String,
    val cardId: String,
    val cardName: String,
    val buyerUserId: String,
    val sellerUserId: String,
    val amountMinor: Long,
    val currency: String,
    val platformFeeMinor: Long,
    val sellerAmountMinor: Long,
    val paymentStatus: PaymentStatus,
    val payoutStatus: PayoutStatus,
    val checkoutSessionId: String? = null,
    val paymentIntentId: String? = null,
    val paidAt: Long? = null,
    val paidOutAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

data class SellerPayoutAccount(
    val userId: String,
    val provider: String,
    val connectedAccountId: String,
    val detailsSubmitted: Boolean,
    val chargesEnabled: Boolean,
    val payoutsEnabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

data class SellerPayoutStatus(
    val accountId: String? = null,
    val detailsSubmitted: Boolean = false,
    val chargesEnabled: Boolean = false,
    val payoutsEnabled: Boolean = false,
)

data class CheckoutSession(
    val sessionId: String,
    val url: String,
)
