package mtg.app.feature.bridge.infrastructure

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.sql.DataSource

class PostgresChatStore(
    private val dataSource: DataSource,
    private val support: PostgresDocumentStoreSupport,
    private val notificationStore: PostgresNotificationStore,
) : ChatArtifactsStore, ChatRouteStore {
    override fun listChats(): JsonObject {
        val entries = mutableMapOf<String, JsonElement>()
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT chat_id, meta FROM chat_documents").use { st ->
                st.executeQuery().use { rs ->
                    while (rs.next()) {
                        val chatId = rs.getString("chat_id")
                        val meta = support.parseJsonObject(rs.getString("meta"))
                        entries[chatId] = buildJsonObject { put("meta", meta) }
                    }
                }
            }
        }
        return JsonObject(entries)
    }

    override fun getChatMeta(chatId: String): JsonObject? {
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT meta FROM chat_documents WHERE chat_id = ?").use { st ->
                st.setString(1, chatId)
                st.executeQuery().use { rs ->
                    if (!rs.next()) return null
                    return support.parseJsonObject(rs.getString("meta"))
                }
            }
        }
    }

    fun upsertChatMeta(chatId: String, payload: JsonObject) {
        support.ensureChatDocument(chatId)
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "UPDATE chat_documents SET meta = ?::jsonb, updated_at = ? WHERE chat_id = ?"
            ).use { st ->
                st.setString(1, payload.toString())
                st.setLong(2, support.nowMillis())
                st.setString(3, chatId)
                st.executeUpdate()
            }
        }
    }

    override fun patchChatMeta(chatId: String, patch: JsonObject) {
        val current = getChatMeta(chatId) ?: JsonObject(emptyMap())
        val merged = buildJsonObject {
            current.forEach { (key, value) -> put(key, value) }
            patch.forEach { (key, value) -> put(key, value) }
        }
        upsertChatMeta(chatId, merged)
    }

    fun deleteChatMeta(chatId: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("DELETE FROM chat_documents WHERE chat_id = ?").use { st ->
                st.setString(1, chatId)
                st.executeUpdate()
            }
        }
    }

    override fun listChatMessages(chatId: String): JsonObject {
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT messages FROM chat_documents WHERE chat_id = ?").use { st ->
                st.setString(1, chatId)
                st.executeQuery().use { rs ->
                    if (!rs.next()) return JsonObject(emptyMap())
                    return support.parseJsonObject(rs.getString("messages"))
                }
            }
        }
    }

    fun upsertChatMessage(chatId: String, messageId: String, payload: JsonObject) {
        support.ensureChatDocument(chatId)
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
                st.setLong(2, support.nowMillis())
                st.setString(3, chatId)
                st.executeUpdate()
            }
        }
    }

    override fun deleteThread(uid: String, chatId: String, counterpartUid: String) {
        deleteChatMeta(chatId)
        support.deleteUserSectionEntry(uid, UserSection.MATCHES, chatId)
        if (counterpartUid.isNotBlank()) {
            support.deleteUserSectionEntry(counterpartUid, UserSection.MATCHES, chatId)
        }
    }

    override fun ensureChatAndArtifacts(
        buyerUid: String,
        buyerEmail: String,
        sellerUid: String,
        sellerEmail: String,
        cardId: String,
        cardName: String,
    ): String {
        val safeCardId = cardId.ifBlank { cardName }
        val chatId = buildChatId(firstUid = sellerUid, secondUid = buyerUid, marketKey = safeCardId)
        val createdAt = support.nowMillis()

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

        support.upsertUserSectionEntry(
            uid = buyerUid,
            section = UserSection.MATCHES,
            key = chatId,
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
        support.upsertUserSectionEntry(
            uid = sellerUid,
            section = UserSection.MATCHES,
            key = chatId,
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

    override fun sendMessage(
        uid: String,
        chatId: String,
        senderDisplayName: String,
        text: String,
    ) {
        val normalizedText = text.trim()
        if (normalizedText.isBlank()) return

        val meta = getChatMeta(chatId) ?: throw IllegalStateException("Chat not found")
        val now = support.nowMillis()
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
            notificationStore.upsertNotification(
                uid = recipientUid,
                notificationId = notificationId,
                payload = buildJsonObject {
                    put("notificationId", notificationId)
                    put("chatId", chatId)
                    put("sellerUid", uid)
                    put("sellerEmail", senderDisplayName)
                    put("cardName", cardName)
                    put("message", "New message about $cardName: ${normalizedText.take(80)}")
                    put("isRead", false)
                    put("type", "new_message")
                }
            )
        }
    }

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
}
