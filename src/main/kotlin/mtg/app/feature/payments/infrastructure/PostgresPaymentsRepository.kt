package mtg.app.feature.payments.infrastructure

import mtg.app.feature.payments.domain.PaymentStatus
import mtg.app.feature.payments.domain.PaymentsRepository
import mtg.app.feature.payments.domain.PayoutStatus
import mtg.app.feature.payments.domain.SellerPayoutAccount
import mtg.app.feature.payments.domain.TradeOrder
import javax.sql.DataSource

class PostgresPaymentsRepository(
    private val dataSource: DataSource,
) : PaymentsRepository {
    override suspend fun findOrderByChatId(chatId: String): TradeOrder? {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT id, chat_id, card_id, card_name, buyer_user_id, seller_user_id,
                       amount_minor, currency, platform_fee_minor, seller_amount_minor,
                       payment_status, payout_status, checkout_session_id, payment_intent_id,
                       paid_at, paid_out_at, created_at, updated_at
                FROM trade_orders
                WHERE chat_id = ?
                """.trimIndent()
            ).use { st ->
                st.setString(1, chatId)
                st.executeQuery().use { rs ->
                    if (!rs.next()) return null
                    return rs.toTradeOrder()
                }
            }
        }
    }

    override suspend fun saveOrder(order: TradeOrder): TradeOrder {
        val existing = findOrderByChatId(order.chatId)
        if (existing != null) return existing
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO trade_orders (
                    id, chat_id, card_id, card_name, buyer_user_id, seller_user_id,
                    amount_minor, currency, platform_fee_minor, seller_amount_minor,
                    payment_status, payout_status, checkout_session_id, payment_intent_id,
                    paid_at, paid_out_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { st ->
                st.setString(1, order.id)
                st.setString(2, order.chatId)
                st.setString(3, order.cardId)
                st.setString(4, order.cardName)
                st.setString(5, order.buyerUserId)
                st.setString(6, order.sellerUserId)
                st.setLong(7, order.amountMinor)
                st.setString(8, order.currency)
                st.setLong(9, order.platformFeeMinor)
                st.setLong(10, order.sellerAmountMinor)
                st.setString(11, order.paymentStatus.name)
                st.setString(12, order.payoutStatus.name)
                st.setString(13, order.checkoutSessionId)
                st.setString(14, order.paymentIntentId)
                st.setNullableLong(15, order.paidAt)
                st.setNullableLong(16, order.paidOutAt)
                st.setLong(17, order.createdAt)
                st.setLong(18, order.updatedAt)
                st.executeUpdate()
            }
        }
        return order
    }

    override suspend fun updateCheckoutSession(orderId: String, sessionId: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE trade_orders SET checkout_session_id = ?, updated_at = ? WHERE id = ?"
            ).use { st ->
                st.setString(1, sessionId)
                st.setLong(2, System.currentTimeMillis())
                st.setString(3, orderId)
                st.executeUpdate()
            }
        }
    }

    override suspend fun markOrderPaid(orderId: String, paymentIntentId: String?, paidAt: Long): TradeOrder? {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                UPDATE trade_orders
                SET payment_status = ?, payment_intent_id = COALESCE(?, payment_intent_id), paid_at = ?, updated_at = ?
                WHERE id = ?
                """.trimIndent()
            ).use { st ->
                st.setString(1, PaymentStatus.PAID.name)
                st.setString(2, paymentIntentId)
                st.setLong(3, paidAt)
                st.setLong(4, paidAt)
                st.setString(5, orderId)
                st.executeUpdate()
            }
        }
        return findOrderById(orderId)
    }

    override suspend fun markOrderPaidOut(orderId: String, paidOutAt: Long): TradeOrder? {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE trade_orders SET payout_status = ?, paid_out_at = ?, updated_at = ? WHERE id = ?"
            ).use { st ->
                st.setString(1, PayoutStatus.PAID_OUT.name)
                st.setLong(2, paidOutAt)
                st.setLong(3, paidOutAt)
                st.setString(4, orderId)
                st.executeUpdate()
            }
        }
        return findOrderById(orderId)
    }

    override suspend fun saveSellerPayoutAccount(account: SellerPayoutAccount): SellerPayoutAccount {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO seller_payout_accounts (
                    user_id, provider, connected_account_id, details_submitted,
                    charges_enabled, payouts_enabled, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE SET
                    provider = EXCLUDED.provider,
                    connected_account_id = EXCLUDED.connected_account_id,
                    details_submitted = EXCLUDED.details_submitted,
                    charges_enabled = EXCLUDED.charges_enabled,
                    payouts_enabled = EXCLUDED.payouts_enabled,
                    updated_at = EXCLUDED.updated_at
                """.trimIndent()
            ).use { st ->
                st.setString(1, account.userId)
                st.setString(2, account.provider)
                st.setString(3, account.connectedAccountId)
                st.setBoolean(4, account.detailsSubmitted)
                st.setBoolean(5, account.chargesEnabled)
                st.setBoolean(6, account.payoutsEnabled)
                st.setLong(7, account.createdAt)
                st.setLong(8, account.updatedAt)
                st.executeUpdate()
            }
        }
        return account
    }

    override suspend fun findSellerPayoutAccount(userId: String): SellerPayoutAccount? {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT user_id, provider, connected_account_id, details_submitted, charges_enabled, payouts_enabled, created_at, updated_at FROM seller_payout_accounts WHERE user_id = ?"
            ).use { st ->
                st.setString(1, userId)
                st.executeQuery().use { rs ->
                    if (!rs.next()) return null
                    return SellerPayoutAccount(
                        userId = rs.getString("user_id"),
                        provider = rs.getString("provider"),
                        connectedAccountId = rs.getString("connected_account_id"),
                        detailsSubmitted = rs.getBoolean("details_submitted"),
                        chargesEnabled = rs.getBoolean("charges_enabled"),
                        payoutsEnabled = rs.getBoolean("payouts_enabled"),
                        createdAt = rs.getLong("created_at"),
                        updatedAt = rs.getLong("updated_at"),
                    )
                }
            }
        }
    }

    private fun findOrderById(orderId: String): TradeOrder? {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "SELECT id, chat_id, card_id, card_name, buyer_user_id, seller_user_id, amount_minor, currency, platform_fee_minor, seller_amount_minor, payment_status, payout_status, checkout_session_id, payment_intent_id, paid_at, paid_out_at, created_at, updated_at FROM trade_orders WHERE id = ?"
            ).use { st ->
                st.setString(1, orderId)
                st.executeQuery().use { rs ->
                    if (!rs.next()) return null
                    return rs.toTradeOrder()
                }
            }
        }
    }

    private fun java.sql.ResultSet.toTradeOrder(): TradeOrder {
        return TradeOrder(
            id = getString("id"),
            chatId = getString("chat_id"),
            cardId = getString("card_id"),
            cardName = getString("card_name"),
            buyerUserId = getString("buyer_user_id"),
            sellerUserId = getString("seller_user_id"),
            amountMinor = getLong("amount_minor"),
            currency = getString("currency"),
            platformFeeMinor = getLong("platform_fee_minor"),
            sellerAmountMinor = getLong("seller_amount_minor"),
            paymentStatus = PaymentStatus.valueOf(getString("payment_status")),
            payoutStatus = PayoutStatus.valueOf(getString("payout_status")),
            checkoutSessionId = getString("checkout_session_id"),
            paymentIntentId = getString("payment_intent_id"),
            paidAt = getNullableLong("paid_at"),
            paidOutAt = getNullableLong("paid_out_at"),
            createdAt = getLong("created_at"),
            updatedAt = getLong("updated_at"),
        )
    }

    private fun java.sql.PreparedStatement.setNullableLong(index: Int, value: Long?) {
        if (value == null) setNull(index, java.sql.Types.BIGINT) else setLong(index, value)
    }

    private fun java.sql.ResultSet.getNullableLong(column: String): Long? {
        val value = getLong(column)
        return if (wasNull()) null else value
    }
}
