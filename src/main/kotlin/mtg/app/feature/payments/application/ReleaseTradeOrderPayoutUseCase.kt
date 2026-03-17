package mtg.app.feature.payments.application

import mtg.app.core.error.ValidationException
import mtg.app.feature.payments.domain.PaymentStatus
import mtg.app.feature.payments.domain.PaymentsRepository
import mtg.app.feature.payments.domain.PayoutStatus
import mtg.app.feature.payments.domain.StripeGateway

class ReleaseTradeOrderPayoutUseCase(
    private val repository: PaymentsRepository,
    private val stripeGateway: StripeGateway,
) {
    suspend operator fun invoke(chatId: String) {
        val order = repository.findOrderByChatId(chatId) ?: return
        if (order.paymentStatus != PaymentStatus.PAID) return
        if (order.payoutStatus == PayoutStatus.PAID_OUT) return

        val payoutAccount = repository.findSellerPayoutAccount(order.sellerUserId)
            ?: throw ValidationException("Seller payout account is not configured")
        if (!payoutAccount.payoutsEnabled) {
            throw ValidationException("Seller payout account is not ready")
        }

        stripeGateway.createTransfer(order = order, destinationAccountId = payoutAccount.connectedAccountId)
        repository.markOrderPaidOut(order.id, System.currentTimeMillis())
    }
}
