package mtg.app.feature.offers.infrastructure

import mtg.app.core.db.getNullableDouble
import mtg.app.core.db.setNullableBigDecimal
import mtg.app.feature.offers.domain.Offer
import mtg.app.feature.offers.domain.OfferRepository
import mtg.app.feature.offers.domain.OfferType
import javax.sql.DataSource

class PostgresOfferRepository(
    private val dataSource: DataSource,
) : OfferRepository {
    override suspend fun create(offer: Offer): Offer {
        val sql = """
            INSERT INTO offers (id, user_id, card_id, card_name, card_type_line, card_image_url, offer_type, price, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, offer.id)
                statement.setString(2, offer.userId)
                statement.setString(3, offer.cardId)
                statement.setString(4, offer.cardName)
                statement.setString(5, offer.cardTypeLine)
                statement.setString(6, offer.cardImageUrl)
                statement.setString(7, offer.type.name)
                statement.setNullableBigDecimal(8, offer.price)
                statement.setLong(9, offer.createdAt)
                statement.executeUpdate()
            }
        }
        return offer
    }

    override suspend fun list(cardId: String?, userId: String?, type: OfferType?): List<Offer> {
        val sql = buildString {
            append(
                """
                SELECT id, user_id, card_id, card_name, card_type_line, card_image_url, offer_type, price, created_at
                FROM offers
                """.trimIndent()
            )

            val filters = mutableListOf<String>()
            if (cardId != null) filters += "card_id = ?"
            if (userId != null) filters += "user_id = ?"
            if (type != null) filters += "offer_type = ?"
            if (filters.isNotEmpty()) {
                append("\nWHERE ")
                append(filters.joinToString(" AND "))
            }
            append("\nORDER BY created_at DESC")
        }

        return buildList {
            dataSource.connection.use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    var idx = 1
                    if (cardId != null) {
                        statement.setString(idx, cardId)
                        idx += 1
                    }
                    if (userId != null) {
                        statement.setString(idx, userId)
                        idx += 1
                    }
                    if (type != null) {
                        statement.setString(idx, type.name)
                    }

                    statement.executeQuery().use { rs ->
                        while (rs.next()) {
                            add(
                                Offer(
                                    id = rs.getString("id"),
                                    userId = rs.getString("user_id"),
                                    cardId = rs.getString("card_id"),
                                    cardName = rs.getString("card_name"),
                                    cardTypeLine = rs.getString("card_type_line"),
                                    cardImageUrl = rs.getString("card_image_url"),
                                    type = rs.getString("offer_type")
                                        ?.let { raw -> runCatching { OfferType.valueOf(raw) }.getOrNull() }
                                        ?: OfferType.SELL,
                                    price = rs.getNullableDouble("price"),
                                    createdAt = rs.getLong("created_at"),
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    override suspend fun deleteOwned(id: String, ownerUserId: String): Boolean {
        val sql = "DELETE FROM offers WHERE id = ? AND user_id = ?"
        val affectedRows = dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, id)
                statement.setString(2, ownerUserId)
                statement.executeUpdate()
            }
        }
        return affectedRows > 0
    }
}
