package mtg.app.feature.bridge.infrastructure

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.sql.DataSource

class PostgresNotificationStore(
    private val dataSource: DataSource,
    private val support: PostgresDocumentStoreSupport,
) {
    fun listNotifications(uid: String): JsonObject = support.readUserSection(uid, UserSection.NOTIFICATIONS)

    fun upsertNotification(uid: String, notificationId: String, payload: JsonObject) {
        support.upsertUserSectionEntry(uid, UserSection.NOTIFICATIONS, notificationId, payload)
    }

    fun markNotificationRead(uid: String, notificationId: String) {
        val notifications = support.readUserSection(uid, UserSection.NOTIFICATIONS)
        val current = notifications[notificationId] as? JsonObject ?: return
        val next = buildJsonObject {
            current.forEach { (key, value) -> put(key, value) }
            put("isRead", true)
        }
        support.upsertUserSectionEntry(uid, UserSection.NOTIFICATIONS, notificationId, next)
    }

    fun deleteNotification(uid: String, notificationId: String) {
        support.deleteUserSectionEntry(uid, UserSection.NOTIFICATIONS, notificationId)
    }

    fun deleteNotificationsForChat(chatId: String) {
        if (chatId.isBlank()) return
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT uid, notifications FROM user_documents").use { st ->
                st.executeQuery().use { rs ->
                    while (rs.next()) {
                        val uid = rs.getString("uid")
                        val notifications = support.parseJsonObject(rs.getString("notifications"))
                        val filtered = notifications.filterValues { value ->
                            val obj = value as? JsonObject ?: return@filterValues true
                            val linkedChatId = (obj["chatId"] as? JsonPrimitive)?.content?.trim().orEmpty()
                            linkedChatId != chatId
                        }
                        if (filtered.size != notifications.size) {
                            support.writeUserSection(uid, UserSection.NOTIFICATIONS, JsonObject(filtered))
                        }
                    }
                }
            }
        }
    }

    fun hasUnreadNotifications(uid: String): Boolean {
        return support.readUserSection(uid, UserSection.NOTIFICATIONS)
            .values
            .asSequence()
            .mapNotNull { it as? JsonObject }
            .any { obj ->
                val readRaw = (obj["isRead"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false
                !readRaw
            }
    }
}
