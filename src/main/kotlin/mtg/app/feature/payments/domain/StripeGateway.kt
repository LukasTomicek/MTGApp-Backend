package mtg.app.feature.payments.domain

interface StripeGateway {
    suspend fun createExpressAccount(email: String?): SellerPayoutStatus
    suspend fun refreshAccountStatus(accountId: String): SellerPayoutStatus
    suspend fun createAccountOnboardingLink(accountId: String): String
    suspend fun createCheckoutSession(order: TradeOrder): CheckoutSession
    suspend fun createTransfer(order: TradeOrder, destinationAccountId: String): String
    fun verifyWebhookSignature(payload: String, signatureHeader: String?): Boolean
}
