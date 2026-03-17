package mtg.app.core.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.ApplicationConfig
import java.math.BigDecimal
import java.sql.PreparedStatement
import java.sql.ResultSet
import javax.sql.DataSource

private const val DEFAULT_DB_URL = "jdbc:postgresql://localhost:5432/mtg_backend"
private const val DEFAULT_DB_USER = "mtg"
private const val DEFAULT_DB_PASSWORD = "mtg"
private const val DEFAULT_DB_DRIVER = "org.postgresql.Driver"
private const val DEFAULT_DB_POOL_SIZE = 10
private val MIGRATIONS = listOf(
    "db/migration/V1__create_offers.sql",
    "db/migration/V2__add_offer_type.sql",
    "db/migration/V3__create_user_profiles.sql",
    "db/migration/V4__create_bridge_state.sql",
    "db/migration/V5__extend_bridge_state.sql",
    "db/migration/V6__extend_offers_with_card_metadata.sql",
    "db/migration/V7__add_offer_composite_indexes.sql",
    "db/migration/V8__create_wallet_tables.sql",
    "db/migration/V9__drop_wallet_tables.sql",
    "db/migration/V10__create_trade_payment_tables.sql",
)

class DatabaseFactory(
    config: ApplicationConfig,
) : AutoCloseable {
    private val dataSource: HikariDataSource = createDataSource(config)

    init {
        migrate()
    }

    fun dataSource(): DataSource = dataSource

    override fun close() {
        dataSource.close()
    }

    private fun migrate() {
        dataSource.connection.use { connection ->
            MIGRATIONS.forEach { migrationPath ->
                val migrationSql = javaClass.classLoader
                    .getResource(migrationPath)
                    ?.readText()
                    ?: error("Missing migration file: $migrationPath")
                connection.createStatement().use { statement ->
                    statement.execute(migrationSql)
                }
            }
        }
    }

    private fun createDataSource(config: ApplicationConfig): HikariDataSource {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.propertyOrNull("storage.jdbc.url")?.getString() ?: DEFAULT_DB_URL
            username = config.propertyOrNull("storage.jdbc.user")?.getString() ?: DEFAULT_DB_USER
            password = config.propertyOrNull("storage.jdbc.password")?.getString() ?: DEFAULT_DB_PASSWORD
            driverClassName = config.propertyOrNull("storage.jdbc.driver")?.getString() ?: DEFAULT_DB_DRIVER
            maximumPoolSize = config.propertyOrNull("storage.jdbc.maxPoolSize")?.getString()?.toIntOrNull()
                ?: DEFAULT_DB_POOL_SIZE
            isAutoCommit = true
            transactionIsolation = "TRANSACTION_READ_COMMITTED"
        }
        return HikariDataSource(hikariConfig)
    }
}

internal fun PreparedStatement.setNullableBigDecimal(index: Int, value: Double?) {
    if (value == null) {
        setObject(index, null)
    } else {
        setBigDecimal(index, BigDecimal.valueOf(value))
    }
}

internal fun ResultSet.getNullableDouble(column: String): Double? {
    val raw = getBigDecimal(column) ?: return null
    return raw.toDouble()
}
