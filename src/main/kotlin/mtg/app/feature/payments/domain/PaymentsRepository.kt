package mtg.app.feature.payments.domain

interface PaymentsRepository {
    suspend fun findOrderByChatId(chatId: String): TradeOrder?
    suspend fun saveOrder(order: TradeOrder): TradeOrder
    suspend fun updateCheckoutSession(orderId: String, sessionId: String)
    suspend fun markOrderPaid(orderId: String, paymentIntentId: String?, paidAt: Long): TradeOrder?
    suspend fun markOrderPaidOut(orderId: String, paidOutAt: Long): TradeOrder?
    suspend fun saveSellerPayoutAccount(account: SellerPayoutAccount): SellerPayoutAccount
    suspend fun findSellerPayoutAccount(userId: String): SellerPayoutAccount?
}
