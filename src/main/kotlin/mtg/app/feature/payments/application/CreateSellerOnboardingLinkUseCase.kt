package mtg.app.feature.payments.application

import mtg.app.feature.payments.domain.PaymentsRepository
import mtg.app.feature.payments.domain.SellerPayoutAccount
import mtg.app.feature.payments.domain.StripeGateway

class CreateSellerOnboardingLinkUseCase(
    private val repository: PaymentsRepository,
    private val stripeGateway: StripeGateway,
) {
    suspend operator fun invoke(userId: String, email: String?): String {
        val existing = repository.findSellerPayoutAccount(userId)
        val accountId = if (existing == null) {
            val created = stripeGateway.createExpressAccount(email)
            val now = System.currentTimeMillis()
            repository.saveSellerPayoutAccount(
                SellerPayoutAccount(
                    userId = userId,
                    provider = "stripe",
                    connectedAccountId = created.accountId.orEmpty(),
                    detailsSubmitted = created.detailsSubmitted,
                    chargesEnabled = created.chargesEnabled,
                    payoutsEnabled = created.payoutsEnabled,
                    createdAt = now,
                    updatedAt = now,
                )
            ).connectedAccountId
        } else {
            existing.connectedAccountId
        }
        return stripeGateway.createAccountOnboardingLink(accountId)
    }
}
