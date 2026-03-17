package mtg.app.feature.payments.application

import mtg.app.feature.payments.domain.PaymentsRepository
import mtg.app.feature.payments.domain.SellerPayoutStatus
import mtg.app.feature.payments.domain.StripeGateway

class GetSellerPayoutStatusUseCase(
    private val repository: PaymentsRepository,
    private val stripeGateway: StripeGateway,
) {
    suspend operator fun invoke(userId: String): SellerPayoutStatus {
        val existing = repository.findSellerPayoutAccount(userId) ?: return SellerPayoutStatus()
        val refreshed = stripeGateway.refreshAccountStatus(existing.connectedAccountId)
        repository.saveSellerPayoutAccount(
            existing.copy(
                detailsSubmitted = refreshed.detailsSubmitted,
                chargesEnabled = refreshed.chargesEnabled,
                payoutsEnabled = refreshed.payoutsEnabled,
                updatedAt = System.currentTimeMillis(),
            )
        )
        return refreshed.copy(accountId = existing.connectedAccountId)
    }
}
