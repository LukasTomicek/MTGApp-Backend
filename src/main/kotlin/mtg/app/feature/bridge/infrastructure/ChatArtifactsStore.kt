package mtg.app.feature.bridge.infrastructure

import kotlinx.serialization.json.JsonObject

interface ChatArtifactsStore {
    fun listChats(): JsonObject

    fun ensureChatAndArtifacts(
        buyerUid: String,
        buyerEmail: String,
        sellerUid: String,
        sellerEmail: String,
        cardId: String,
        cardName: String,
    ): String
}
