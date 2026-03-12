package mtg.app.feature.bridge.infrastructure

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mtg.app.core.db.getNullableDouble
import mtg.app.feature.offers.domain.OfferType
import javax.sql.DataSource

class PostgresBridgeRepository(
    private val dataSource: DataSource,
    private val support: PostgresDocumentStoreSupport,
) : MarketplaceMapPinsStore {
    fun listUserMatches(uid: String): JsonObject = support.readUserSection(uid, UserSection.MATCHES)

    fun listUserCollection(uid: String): JsonObject = support.readUserSection(uid, UserSection.COLLECTION)

    fun upsertUserCollectionEntry(uid: String, entryId: String, payload: JsonObject) {
        support.upsertUserSectionEntry(uid, UserSection.COLLECTION, entryId, payload)
    }

    fun deleteUserCollectionEntry(uid: String, entryId: String) {
        support.deleteUserSectionEntry(uid, UserSection.COLLECTION, entryId)
    }

    fun clearUserCollection(uid: String) {
        support.writeUserSection(uid, UserSection.COLLECTION, JsonObject(emptyMap()))
    }

    fun loadOnboardingCompleted(uid: String): Boolean {
        support.ensureUserDocument(uid)
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT onboarding_completed FROM user_documents WHERE uid = ?").use { st ->
                st.setString(1, uid)
                st.executeQuery().use { rs ->
                    if (!rs.next()) return false
                    return rs.getBoolean("onboarding_completed")
                }
            }
        }
    }

    fun updateOnboardingCompleted(uid: String, completed: Boolean) {
        support.ensureUserDocument(uid)
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE user_documents SET onboarding_completed = ?, updated_at = ? WHERE uid = ?"
            ).use { st ->
                st.setBoolean(1, completed)
                st.setLong(2, support.nowMillis())
                st.setString(3, uid)
                st.executeUpdate()
            }
        }
    }

    fun listUserMapPins(uid: String): JsonObject = support.readUserSection(uid, UserSection.MAP_PINS)

    fun replaceUserMapPins(uid: String, payload: JsonObject) {
        support.writeUserSection(uid, UserSection.MAP_PINS, payload)
    }

    override fun listMarketplaceMapPinsByUser(): JsonObject {
        val byUser = linkedMapOf<String, JsonElement>()
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT uid, map_pins FROM user_documents").use { st ->
                st.executeQuery().use { rs ->
                    while (rs.next()) {
                        val uid = rs.getString("uid")
                        val pins = support.parseJsonObject(rs.getString("map_pins"))
                        byUser[uid] = pins
                    }
                }
            }
        }
        return JsonObject(byUser)
    }

    fun listMarketplaceSellEntriesByUser(uid: String): JsonObject {
        return listMarketplaceEntriesByUser(uid = uid, type = OfferType.SELL)
    }

    fun listMarketplaceSellEntriesByUsers(): JsonObject {
        return listMarketplaceEntriesByUsers(type = OfferType.SELL)
    }

    fun listMarketplaceBuyEntriesByUsers(): JsonObject {
        return listMarketplaceEntriesByUsers(type = OfferType.BUY)
    }

    private fun listMarketplaceEntriesByUsers(type: OfferType): JsonObject {
        val sql = """
            SELECT id, user_id, card_id, card_name, card_type_line, card_image_url, price
            FROM offers
            WHERE offer_type = ?
            ORDER BY created_at DESC
        """.trimIndent()

        val resultByUser = linkedMapOf<String, MutableMap<String, JsonElement>>()
        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { st ->
                st.setString(1, type.name)
                st.executeQuery().use { rs ->
                    while (rs.next()) {
                        val uid = rs.getString("user_id")
                        val id = rs.getString("id")
                        val userEntries = resultByUser.getOrPut(uid) { linkedMapOf() }
                        userEntries[id] = entryJsonFromOfferRow(rs)
                    }
                }
            }
        }
        return JsonObject(resultByUser.mapValues { JsonObject(it.value) })
    }

    private fun listMarketplaceEntriesByUser(uid: String, type: OfferType): JsonObject {
        val sql = """
            SELECT id, card_id, card_name, card_type_line, card_image_url, price
            FROM offers
            WHERE user_id = ? AND offer_type = ?
            ORDER BY created_at DESC
        """.trimIndent()

        val result = mutableMapOf<String, JsonElement>()
        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { st ->
                st.setString(1, uid)
                st.setString(2, type.name)
                st.executeQuery().use { rs ->
                    while (rs.next()) {
                        val id = rs.getString("id")
                        result[id] = entryJsonFromOfferRow(rs)
                    }
                }
            }
        }
        return JsonObject(result)
    }

    private fun entryJsonFromOfferRow(rs: java.sql.ResultSet): JsonObject {
        return buildJsonObject {
            put("entryId", rs.getString("id"))
            put("cardId", rs.getString("card_id"))
            put("cardName", rs.getString("card_name"))
            rs.getString("card_type_line")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { put("cardTypeLine", it) }
            rs.getString("card_image_url")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    put("cardImageUrl", it)
                    put("artImageUrl", it)
                }
            rs.getNullableDouble("price")?.let { put("price", it) }
        }
    }
}
