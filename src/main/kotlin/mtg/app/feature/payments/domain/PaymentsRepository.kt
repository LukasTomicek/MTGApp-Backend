package mtg.app.feature.payments.domain

interface PaymentsRepository {
    suspend fun calculateSellerBalanceMinor(userId: String): Long
    suspend fun findOrderById(orderId: String): TradeOrder?
    suspend fun findOrderByChatId(chatId: String): TradeOrder?
    suspend fun saveOrder(order: TradeOrder): TradeOrder
    suspend fun updateCheckoutSession(orderId: String, sessionId: String)
    suspend fun markOrderPaid(orderId: String, paymentIntentId: String?, paidAt: Long): TradeOrder?
    suspend fun markOrderPaidOut(orderId: String, paidOutAt: Long): TradeOrder?
    suspend fun updateOrderPaymentStatus(orderId: String, status: PaymentStatus, updatedAt: Long): TradeOrder?
    suspend fun listOrdersBoughtByUser(userId: String): List<TradeOrder>
    suspend fun listOrdersSoldByUser(userId: String): List<TradeOrder>
    suspend fun recordWebhookEvent(eventId: String, eventType: String, orderId: String?, payload: String, receivedAt: Long): Boolean
    suspend fun saveSellerPayoutAccount(account: SellerPayoutAccount): SellerPayoutAccount
    suspend fun findSellerPayoutAccount(userId: String): SellerPayoutAccount?
    suspend fun findSellerPayoutAccountByConnectedAccountId(accountId: String): SellerPayoutAccount?
}
