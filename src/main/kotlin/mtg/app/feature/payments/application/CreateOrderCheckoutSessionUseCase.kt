package mtg.app.feature.payments.application

import mtg.app.core.error.ForbiddenException
import mtg.app.core.error.ValidationException
import mtg.app.feature.payments.domain.CheckoutSession
import mtg.app.feature.payments.domain.PaymentStatus
import mtg.app.feature.payments.domain.PaymentsRepository
import mtg.app.feature.payments.domain.StripeGateway

class CreateOrderCheckoutSessionUseCase(
    private val repository: PaymentsRepository,
    private val stripeGateway: StripeGateway,
) {
    suspend operator fun invoke(orderId: String, buyerUserId: String): CheckoutSession {
        val order = repository.findOrderByChatId(orderId)
            ?: throw ValidationException("Order not found")
        if (order.buyerUserId != buyerUserId) throw ForbiddenException("Only buyer can pay for this order")
        if (order.paymentStatus == PaymentStatus.PAID) throw ValidationException("Order is already paid")

        val session = stripeGateway.createCheckoutSession(order)
        repository.updateCheckoutSession(order.id, session.sessionId)
        return session
    }
}
