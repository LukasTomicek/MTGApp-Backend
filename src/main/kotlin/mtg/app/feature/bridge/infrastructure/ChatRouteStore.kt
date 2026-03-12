package mtg.app.feature.bridge.infrastructure

import kotlinx.serialization.json.JsonObject

interface ChatRouteStore {
    fun listChats(): JsonObject
    fun getChatMeta(chatId: String): JsonObject?
    fun patchChatMeta(chatId: String, patch: JsonObject)
    fun listChatMessages(chatId: String): JsonObject
    fun sendMessage(uid: String, chatId: String, senderDisplayName: String, text: String)
    fun deleteThread(uid: String, chatId: String, counterpartUid: String)
    fun ensureChatAndArtifacts(
        buyerUid: String,
        buyerEmail: String,
        sellerUid: String,
        sellerEmail: String,
        cardId: String,
        cardName: String,
    ): String
}
