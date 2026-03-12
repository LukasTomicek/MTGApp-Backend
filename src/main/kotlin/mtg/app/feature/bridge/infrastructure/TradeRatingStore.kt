package mtg.app.feature.bridge.infrastructure

import kotlinx.serialization.json.JsonObject

interface TradeRatingStore {
    fun hasRatedChat(uid: String, chatId: String): Boolean
    fun listReceivedRatings(uid: String): JsonObject
    fun saveGivenRating(uid: String, chatId: String, payload: JsonObject)
    fun saveReceivedRating(uid: String, ratingId: String, payload: JsonObject)
    fun updateUserProfileRating(uid: String, average: Double, count: Int)
}
