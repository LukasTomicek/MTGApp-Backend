package mtg.app.feature.wallet.infrastructure

import mtg.app.core.error.ValidationException
import mtg.app.feature.wallet.domain.CreditProductCatalog
import mtg.app.feature.wallet.domain.VerifiedWalletPurchase
import mtg.app.feature.wallet.domain.WalletRepository
import kotlin.time.Clock
import javax.sql.DataSource

class PostgresWalletRepository(
    private val dataSource: DataSource,
    private val productCatalog: CreditProductCatalog,
) : WalletRepository {
    override suspend fun loadBalance(userId: String): Int {
        val now = Clock.System.now().toEpochMilliseconds()
        dataSource.connection.use { connection ->
            ensureWalletExists(connection = connection, userId = userId, now = now)
            connection.prepareStatement(
                "SELECT credits_balance FROM user_wallets WHERE user_id = ?"
            ).use { statement ->
                statement.setString(1, userId)
                statement.executeQuery().use { rs ->
                    return if (rs.next()) rs.getInt("credits_balance") else 0
                }
            }
        }
    }

    override suspend fun confirmPurchase(
        userId: String,
        purchase: VerifiedWalletPurchase,
    ): Int {
        val credits = productCatalog.creditsFor(
            platform = purchase.platform,
            productId = purchase.productId,
        )
        val now = Clock.System.now().toEpochMilliseconds()

        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                ensureWalletExists(connection = connection, userId = userId, now = now)

                val existingPurchaseOwner = connection.prepareStatement(
                    "SELECT user_id FROM wallet_purchases WHERE store_platform = ? AND store_transaction_id = ?"
                ).use { statement ->
                    statement.setString(1, purchase.platform.name)
                    statement.setString(2, purchase.storeTransactionId)
                    statement.executeQuery().use { rs -> if (rs.next()) rs.getString("user_id") else null }
                }

                if (existingPurchaseOwner != null && existingPurchaseOwner != userId) {
                    throw ValidationException("Purchase already belongs to another user")
                }

                if (existingPurchaseOwner == null) {
                    connection.prepareStatement(
                        """
                        INSERT INTO wallet_purchases (
                            user_id,
                            store_platform,
                            product_id,
                            store_transaction_id,
                            purchase_token,
                            credits_granted,
                            created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?)
                        """.trimIndent()
                    ).use { statement ->
                        statement.setString(1, userId)
                        statement.setString(2, purchase.platform.name)
                        statement.setString(3, purchase.productId)
                        statement.setString(4, purchase.storeTransactionId)
                        statement.setString(5, purchase.purchaseToken)
                        statement.setInt(6, credits)
                        statement.setLong(7, now)
                        statement.executeUpdate()
                    }

                    connection.prepareStatement(
                        "UPDATE user_wallets SET credits_balance = credits_balance + ?, updated_at = ? WHERE user_id = ?"
                    ).use { statement ->
                        statement.setInt(1, credits)
                        statement.setLong(2, now)
                        statement.setString(3, userId)
                        statement.executeUpdate()
                    }

                    connection.prepareStatement(
                        """
                        INSERT INTO wallet_ledger_entries (
                            user_id,
                            entry_type,
                            amount,
                            reference_type,
                            reference_id,
                            created_at
                        ) VALUES (?, ?, ?, ?, ?, ?)
                        """.trimIndent()
                    ).use { statement ->
                        statement.setString(1, userId)
                        statement.setString(2, "purchase")
                        statement.setInt(3, credits)
                        statement.setString(4, "store_transaction")
                        statement.setString(5, purchase.storeTransactionId)
                        statement.setLong(6, now)
                        statement.executeUpdate()
                    }
                }

                val balance = connection.prepareStatement(
                    "SELECT credits_balance FROM user_wallets WHERE user_id = ?"
                ).use { statement ->
                    statement.setString(1, userId)
                    statement.executeQuery().use { rs -> if (rs.next()) rs.getInt("credits_balance") else 0 }
                }

                connection.commit()
                return balance
            } catch (t: Throwable) {
                runCatching { connection.rollback() }
                throw t
            } finally {
                connection.autoCommit = true
            }
        }
    }

    private fun ensureWalletExists(connection: java.sql.Connection, userId: String, now: Long) {
        connection.prepareStatement(
            """
            INSERT INTO user_wallets (user_id, credits_balance, updated_at)
            VALUES (?, 0, ?)
            ON CONFLICT (user_id) DO NOTHING
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, userId)
            statement.setLong(2, now)
            statement.executeUpdate()
        }
    }
}
