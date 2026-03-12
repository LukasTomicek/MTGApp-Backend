package mtg.app.feature.bridge.infrastructure

import kotlinx.serialization.json.JsonObject

interface UserNotificationStore {
    fun upsertNotification(uid: String, notificationId: String, payload: JsonObject)
    fun deleteNotificationsForChat(chatId: String)
}
