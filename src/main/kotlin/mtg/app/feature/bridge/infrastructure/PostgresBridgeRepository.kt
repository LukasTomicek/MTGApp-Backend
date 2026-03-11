package mtg.app.feature.bridge.infrastructure

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mtg.app.core.db.getNullableDouble
import mtg.app.feature.offers.domain.OfferType
import kotlin.time.Clock
import javax.sql.DataSource

class PostgresBridgeRepository(
    private val dataSource: DataSource,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun listUserMatches(uid: String): JsonObject = readUserSection(uid, UserSection.MATCHES)

    suspend fun upsertUserMatch(uid: String, chatId: String, payload: JsonObject) {
        upsertUserSectionEntry(uid, UserSection.MATCHES, chatId, payload)
    }

    suspend fun deleteUserMatch(uid: String, chatId: String) {
        deleteUserSectionEntry(uid, UserSection.MATCHES, chatId)
    }

    suspend fun listUserCollection(uid: String): JsonObject = readUserSection(uid, UserSection.COLLECTION)

    suspend fun upsertUserCollectionEntry(uid: String, entryId: String, payload: JsonObject) {
        upsertUserSectionEntry(uid, UserSection.COLLECTION, entryId, payload)
    }

    suspend fun deleteUserCollectionEntry(uid: String, entryId: String) {
        deleteUserSectionEntry(uid, UserSection.COLLECTION, entryId)
    }

    suspend fun clearUserCollection(uid: String) {
        writeUserSection(uid, UserSection.COLLECTION, JsonObject(emptyMap()))
    }

    suspend fun listNotifications(uid: String): JsonObject = readUserSection(uid, UserSection.NOTIFICATIONS)

    suspend fun upsertNotification(uid: String, notificationId: String, payload: JsonObject) {
        upsertUserSectionEntry(uid, UserSection.NOTIFICATIONS, notificationId, payload)
    }

    suspend fun markNotificationRead(uid: String, notificationId: String) {
        val notifications = readUserSection(uid, UserSection.NOTIFICATIONS)
        val current = notifications[notificationId] as? JsonObject ?: return
        val next = buildJsonObject {
            current.forEach { (key, value) -> put(key, value) }
            put("isRead", true)
        }
        upsertUserSectionEntry(uid, UserSection.NOTIFICATIONS, notificationId, next)
    }

    suspend fun deleteNotification(uid: String, notificationId: String) {
        deleteUserSectionEntry(uid, UserSection.NOTIFICATIONS, notificationId)
    }

    suspend fun hasUnreadNotifications(uid: String): Boolean {
        return readUserSection(uid, UserSection.NOTIFICATIONS)
            .values
            .asSequence()
            .mapNotNull { it as? JsonObject }
            .any { obj ->
                val readRaw = (obj["isRead"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false
                !readRaw
            }
    }

    suspend fun hasRatedChat(uid: String, chatId: String): Boolean {
        val given = readUserSection(uid, UserSection.GIVEN_RATINGS)
        return given[chatId] != null
    }

    suspend fun listReceivedRatings(uid: String): JsonObject = readUserSection(uid, UserSection.RECEIVED_RATINGS)

    suspend fun saveGivenRating(uid: String, chatId: String, payload: JsonObject) {
        upsertUserSectionEntry(uid, UserSection.GIVEN_RATINGS, chatId, payload)
    }

    suspend fun saveReceivedRating(uid: String, ratingId: String, payload: JsonObject) {
        upsertUserSectionEntry(uid, UserSection.RECEIVED_RATINGS, ratingId, payload)
    }

    suspend fun updateUserProfileRating(uid: String, average: Double, count: Int) {
        val now = nowMillis()
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO user_documents (uid, updated_at, rating_average, rating_count)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (uid)
                DO UPDATE SET updated_at = EXCLUDED.updated_at,
                              rating_average = EXCLUDED.rating_average,
                              rating_count = EXCLUDED.rating_count
                """.trimIndent()
            ).use { st ->
                st.setString(1, uid)
                st.setLong(2, now)
                st.setDouble(3, average)
                st.setInt(4, count.coerceAtLeast(0))
                st.executeUpdate()
            }

            connection.prepareStatement(
                """
                UPDATE user_profiles
                SET rating_average = ?, rating_count = ?
                WHERE user_id = ?
                """.trimIndent()
            ).use { st ->
                st.setDouble(1, average)
                st.setInt(2, count.coerceAtLeast(0))
                st.setString(3, uid)
                st.executeUpdate()
            }
        }
    }

    suspend fun loadOnboardingCompleted(uid: String): Boolean {
        ensureUserDocument(uid)
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

    suspend fun updateOnboardingCompleted(uid: String, completed: Boolean) {
        ensureUserDocument(uid)
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE user_documents SET onboarding_completed = ?, updated_at = ? WHERE uid = ?"
            ).use { st ->
                st.setBoolean(1, completed)
                st.setLong(2, nowMillis())
                st.setString(3, uid)
                st.executeUpdate()
            }
        }
    }

    suspend fun listUserMapPins(uid: String): JsonObject = readUserSection(uid, UserSection.MAP_PINS)

    suspend fun replaceUserMapPins(uid: String, payload: JsonObject) {
        writeUserSection(uid, UserSection.MAP_PINS, payload)
    }

    suspend fun listMarketplaceMapPinsByUser(): JsonObject {
        val byUser = linkedMapOf<String, JsonElement>()
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT uid, map_pins FROM user_documents").use { st ->
                st.executeQuery().use { rs ->
                    while (rs.next()) {
                        val uid = rs.getString("uid")
                        val pins = parseJsonObject(rs.getString("map_pins"))
                        byUser[uid] = pins
                    }
                }
            }
        }
        return JsonObject(byUser)
    }

    suspend fun listChats(): JsonObject {
        val entries = mutableMapOf<String, JsonElement>()
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT chat_id, meta FROM chat_documents").use { st ->
                st.executeQuery().use { rs ->
                    while (rs.next()) {
                        val chatId = rs.getString("chat_id")
                        val meta = parseJsonObject(rs.getString("meta"))
                        entries[chatId] = buildJsonObject { put("meta", meta) }
                    }
                }
            }
        }
        return JsonObject(entries)
    }

    suspend fun getChatMeta(chatId: String): JsonObject? {
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT meta FROM chat_documents WHERE chat_id = ?").use { st ->
                st.setString(1, chatId)
                st.executeQuery().use { rs ->
                    if (!rs.next()) return null
                    return parseJsonObject(rs.getString("meta"))
                }
            }
        }
    }

    suspend fun upsertChatMeta(chatId: String, payload: JsonObject) {
        ensureChatDocument(chatId)
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE chat_documents SET meta = ?::jsonb, updated_at = ? WHERE chat_id = ?"
            ).use { st ->
                st.setString(1, payload.toString())
                st.setLong(2, nowMillis())
                st.setString(3, chatId)
                st.executeUpdate()
            }
        }
    }

    suspend fun patchChatMeta(chatId: String, patch: JsonObject) {
        val current = getChatMeta(chatId) ?: JsonObject(emptyMap())
        val merged = buildJsonObject {
            current.forEach { (key, value) -> put(key, value) }
            patch.forEach { (key, value) -> put(key, value) }
        }
        upsertChatMeta(chatId, merged)
    }

    suspend fun deleteChatMeta(chatId: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("DELETE FROM chat_documents WHERE chat_id = ?").use { st ->
                st.setString(1, chatId)
                st.executeUpdate()
            }
        }
    }

    suspend fun listChatMessages(chatId: String): JsonObject {
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT messages FROM chat_documents WHERE chat_id = ?").use { st ->
                st.setString(1, chatId)
                st.executeQuery().use { rs ->
                    if (!rs.next()) return JsonObject(emptyMap())
                    return parseJsonObject(rs.getString("messages"))
                }
            }
        }
    }

    suspend fun upsertChatMessage(chatId: String, messageId: String, payload: JsonObject) {
        ensureChatDocument(chatId)
        val current = listChatMessages(chatId)
        val next = buildJsonObject {
            current.forEach { (key, value) -> put(key, value) }
            put(messageId, payload)
        }
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE chat_documents SET messages = ?::jsonb, updated_at = ? WHERE chat_id = ?"
            ).use { st ->
                st.setString(1, next.toString())
                st.setLong(2, nowMillis())
                st.setString(3, chatId)
                st.executeUpdate()
            }
        }
    }

    suspend fun deleteChatMessages(chatId: String) {
        ensureChatDocument(chatId)
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE chat_documents SET messages = '{}'::jsonb, updated_at = ? WHERE chat_id = ?"
            ).use { st ->
                st.setLong(1, nowMillis())
                st.setString(2, chatId)
                st.executeUpdate()
            }
        }
    }

    suspend fun deleteThread(uid: String, chatId: String, counterpartUid: String) {
        deleteChatMeta(chatId)
        deleteUserMatch(uid, chatId)
        if (counterpartUid.isNotBlank()) {
            deleteUserMatch(counterpartUid, chatId)
        }
    }

    suspend fun listMarketplaceSellEntriesByUser(uid: String): JsonObject {
        return listMarketplaceEntriesByUser(uid = uid, type = OfferType.SELL)
    }

    suspend fun listMarketplaceSellEntriesByUsers(): JsonObject {
        return listMarketplaceEntriesByUsers(type = OfferType.SELL)
    }

    suspend fun listMarketplaceBuyEntriesByUsers(): JsonObject {
        return listMarketplaceEntriesByUsers(type = OfferType.BUY)
    }

    suspend fun ensureChatAndArtifacts(
        buyerUid: String,
        buyerEmail: String,
        sellerUid: String,
        sellerEmail: String,
        cardId: String,
        cardName: String,
    ): String {
        val safeCardId = cardId.ifBlank { cardName }
        val chatId = buildChatId(firstUid = sellerUid, secondUid = buyerUid, marketKey = safeCardId)
        val createdAt = nowMillis()

        val existingMeta = getChatMeta(chatId)
        if (existingMeta == null) {
            val meta = buildJsonObject {
                put("chatId", chatId)
                put("buyerUid", buyerUid)
                put("buyerEmail", buyerEmail)
                put("sellerUid", sellerUid)
                put("sellerEmail", sellerEmail)
                put("cardId", safeCardId)
                put("cardName", cardName)
                put("createdAt", createdAt)
                put("dealStatus", "OPEN")
                put("buyerConfirmed", false)
                put("sellerConfirmed", false)
            }
            upsertChatMeta(chatId, meta)
        }

        upsertUserMatch(
            uid = buyerUid,
            chatId = chatId,
            payload = buildJsonObject {
                put("chatId", chatId)
                put("counterpartUid", sellerUid)
                put("counterpartEmail", sellerEmail)
                put("cardId", safeCardId)
                put("cardName", cardName)
                put("role", "buyer")
                put("updatedAt", createdAt)
            }
        )
        upsertUserMatch(
            uid = sellerUid,
            chatId = chatId,
            payload = buildJsonObject {
                put("chatId", chatId)
                put("counterpartUid", buyerUid)
                put("counterpartEmail", buyerEmail)
                put("cardId", safeCardId)
                put("cardName", cardName)
                put("role", "seller")
                put("updatedAt", createdAt)
            }
        )

        return chatId
    }

    suspend fun sendMessage(
        uid: String,
        chatId: String,
        senderDisplayName: String,
        text: String,
    ) {
        val normalizedText = text.trim()
        if (normalizedText.isBlank()) return

        val meta = getChatMeta(chatId) ?: throw IllegalStateException("Chat not found")
        val now = nowMillis()
        val messageId = "msg_${now}_${uid.takeLast(8).ifBlank { "user" }}"

        upsertChatMessage(
            chatId = chatId,
            messageId = messageId,
            payload = buildJsonObject {
                put("messageId", messageId)
                put("senderUid", uid)
                put("senderEmail", senderDisplayName)
                put("text", normalizedText)
                put("createdAt", now)
            }
        )

        patchChatMeta(
            chatId = chatId,
            patch = buildJsonObject {
                put("lastMessage", normalizedText)
                put("lastMessageAt", now)
            }
        )

        val buyerUid = (meta["buyerUid"] as? JsonPrimitive)?.content.orEmpty()
        val sellerUid = (meta["sellerUid"] as? JsonPrimitive)?.content.orEmpty()
        val cardName = (meta["cardName"] as? JsonPrimitive)?.content.orEmpty().ifBlank { "Chat" }
        val recipientUid = when (uid) {
            buyerUid -> sellerUid
            sellerUid -> buyerUid
            else -> ""
        }

        if (recipientUid.isNotBlank()) {
            val notificationId = "chat_msg_${chatId.lowercase()}_${now}_${uid.takeLast(8).ifBlank { "user" }}"
                .replace(":", "_")
                .replace("/", "_")
                .replace(".", "_")
            val messagePreview = normalizedText.take(80)
            upsertNotification(
                uid = recipientUid,
                notificationId = notificationId,
                payload = buildJsonObject {
                    put("notificationId", notificationId)
                    put("chatId", chatId)
                    put("sellerUid", uid)
                    put("sellerEmail", senderDisplayName)
                    put("cardName", cardName)
                    put("message", "New message about $cardName: $messagePreview")
                    put("isRead", false)
                    put("type", "new_message")
                }
            )
        }
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

    private fun ensureChatDocument(chatId: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO chat_documents (chat_id, updated_at)
                VALUES (?, ?)
                ON CONFLICT (chat_id) DO NOTHING
                """.trimIndent()
            ).use { st ->
                st.setString(1, chatId)
                st.setLong(2, nowMillis())
                st.executeUpdate()
            }
        }
    }

    private fun readUserSection(uid: String, section: UserSection): JsonObject {
        ensureUserDocument(uid)
        val sql = "SELECT ${section.column} FROM user_documents WHERE uid = ?"
        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { st ->
                st.setString(1, uid)
                st.executeQuery().use { rs ->
                    if (!rs.next()) return JsonObject(emptyMap())
                    return parseJsonObject(rs.getString(section.column))
                }
            }
        }
    }

    private fun upsertUserSectionEntry(uid: String, section: UserSection, key: String, payload: JsonObject) {
        val current = readUserSection(uid, section)
        val next = buildJsonObject {
            current.forEach { (k, v) -> put(k, v) }
            put(key, payload)
        }
        writeUserSection(uid, section, next)
    }

    private fun deleteUserSectionEntry(uid: String, section: UserSection, key: String) {
        val current = readUserSection(uid, section)
        if (!current.containsKey(key)) return
        val next = JsonObject(current.filterKeys { it != key })
        writeUserSection(uid, section, next)
    }

    private fun writeUserSection(uid: String, section: UserSection, payload: JsonObject) {
        ensureUserDocument(uid)
        val sql = "UPDATE user_documents SET ${section.column} = ?::jsonb, updated_at = ? WHERE uid = ?"
        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { st ->
                st.setString(1, payload.toString())
                st.setLong(2, nowMillis())
                st.setString(3, uid)
                st.executeUpdate()
            }
        }
    }

    private fun ensureUserDocument(uid: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO user_documents (uid, updated_at)
                VALUES (?, ?)
                ON CONFLICT (uid) DO NOTHING
                """.trimIndent()
            ).use { st ->
                st.setString(1, uid)
                st.setLong(2, nowMillis())
                st.executeUpdate()
            }
        }
    }

    private fun parseJsonObject(raw: String?): JsonObject {
        if (raw.isNullOrBlank()) return JsonObject(emptyMap())
        return runCatching { json.parseToJsonElement(raw) as? JsonObject }
            .getOrNull()
            ?: JsonObject(emptyMap())
    }

    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    private fun buildChatId(firstUid: String, secondUid: String, marketKey: String): String {
        val normalizedUids = listOf(firstUid, secondUid)
            .map { sanitizePart(it) }
            .sorted()
        val keyPart = sanitizePart(marketKey)
        return "chat_${normalizedUids[0]}_${normalizedUids[1]}_id_$keyPart"
    }

    private fun sanitizePart(raw: String): String {
        return raw.lowercase().replace("[^a-z0-9_]".toRegex(), "_")
    }

    private enum class UserSection(val column: String) {
        COLLECTION("collection"),
        MAP_PINS("map_pins"),
        MATCHES("matches"),
        NOTIFICATIONS("notifications"),
        GIVEN_RATINGS("given_ratings"),
        RECEIVED_RATINGS("received_ratings"),
    }
}
