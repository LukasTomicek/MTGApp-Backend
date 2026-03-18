package mtg.app.feature.payments.application

import mtg.app.core.error.ForbiddenException
import mtg.app.core.error.ValidationException
import mtg.app.feature.payments.domain.PaymentStatus
import mtg.app.feature.payments.domain.PaymentsRepository
import mtg.app.feature.payments.domain.PayoutStatus
import mtg.app.feature.payments.domain.StripeGateway
import mtg.app.feature.payments.domain.TradeOrder

class RefundOrderUseCase(
    private val repository: PaymentsRepository,
    private val stripeGateway: StripeGateway,
) {
    suspend operator fun invoke(orderId: String, requesterUserId: String): TradeOrder {
        val order = repository.findOrderById(orderId)
            ?: throw ValidationException("Order not found")

        if (order.buyerUserId != requesterUserId) {
            throw ForbiddenException("Only buyer can refund this order")
        }
        if (order.paymentStatus != PaymentStatus.PAID) {
            throw ValidationException("Only paid orders can be refunded")
        }
        if (order.payoutStatus == PayoutStatus.PAID_OUT) {
            throw ValidationException("Order cannot be refunded after payout")
        }
        val paymentIntentId = order.paymentIntentId?.trim().orEmpty()
        if (paymentIntentId.isBlank()) {
            throw ValidationException("Missing payment intent for refund")
        }

        stripeGateway.createRefund(paymentIntentId = paymentIntentId)
        return repository.updateOrderPaymentStatus(
            orderId = orderId,
            status = PaymentStatus.REFUNDED,
            updatedAt = System.currentTimeMillis(),
        ) ?: throw ValidationException("Order not found after refund")
    }
}
