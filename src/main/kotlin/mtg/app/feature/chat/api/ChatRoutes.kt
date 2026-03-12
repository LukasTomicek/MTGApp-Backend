package mtg.app.feature.chat.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mtg.app.core.auth.FirebaseAuthVerifier
import mtg.app.core.auth.FirebasePrincipal
import mtg.app.core.auth.requireFirebasePrincipal
import mtg.app.core.error.ForbiddenException
import mtg.app.core.error.ValidationException
import mtg.app.feature.bridge.infrastructure.PostgresBridgeRepository
import mtg.app.feature.offers.domain.OfferRepository
import mtg.app.feature.offers.domain.OfferType
import kotlin.time.Clock

@Serializable
private data class EnsureChatRequest(
    val buyerUid: String,
    val buyerEmail: String,
    val sellerUid: String,
    val sellerEmail: String,
    val cardId: String,
    val cardName: String,
)

@Serializable
private data class EnsureChatResponse(
    val chatId: String,
)

@Serializable
private data class SendMessageRequest(
    val senderDisplayName: String,
    val text: String,
)

@Serializable
private data class SubmitRatingRequest(
    val ratedUid: String,
    val score: Int,
    val comment: String = "",
)

fun Route.registerChatRoutes(
    authVerifier: FirebaseAuthVerifier,
    bridgeRepository: PostgresBridgeRepository,
    offerRepository: OfferRepository,
) {
    route("/v1/chats") {
        get {
            val principal = call.requireFirebasePrincipal(authVerifier)
            val chats = bridgeRepository.listChats()
            val filtered = linkedMapOf<String, JsonElement>()
            chats.forEach { (chatId, value) ->
                val root = value as? JsonObject ?: return@forEach
                val meta = root["meta"] as? JsonObject ?: return@forEach
                val buyerUid = meta.stringOrEmpty("buyerUid")
                val sellerUid = meta.stringOrEmpty("sellerUid")
                if (principal.uid == buyerUid || principal.uid == sellerUid) {
                    filtered[chatId] = root
                }
            }
            call.respond(JsonObject(filtered))
        }

        post("/ensure") {
            val principal = call.requireFirebasePrincipal(authVerifier)
            val request = call.receive<EnsureChatRequest>()
            if (principal.uid != request.buyerUid && principal.uid != request.sellerUid) {
                throw ForbiddenException("Authenticated user must be a participant of the chat")
            }
            val chatId = bridgeRepository.ensureChatAndArtifacts(
                buyerUid = request.buyerUid,
                buyerEmail = request.buyerEmail,
                sellerUid = request.sellerUid,
                sellerEmail = request.sellerEmail,
                cardId = request.cardId,
                cardName = request.cardName,
            )
            call.respond(EnsureChatResponse(chatId = chatId))
        }

        get("/{chatId}") {
            val chatId = call.parameters["chatId"].orEmpty()
            val (_, meta) = call.requireChatParticipant(authVerifier, bridgeRepository, chatId) ?: return@get
            call.respond(meta)
        }

        patch("/{chatId}") {
            val chatId = call.parameters["chatId"].orEmpty()
            call.requireChatParticipant(authVerifier, bridgeRepository, chatId) ?: return@patch
            val patch = call.receive<JsonObject>()
            bridgeRepository.patchChatMeta(chatId = chatId, patch = patch)
            val updatedMeta = bridgeRepository.getChatMeta(chatId) ?: JsonObject(emptyMap())
            val closed = (updatedMeta["closed"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() == true
            val dealStatus = (updatedMeta["dealStatus"] as? JsonPrimitive)?.content.orEmpty()
            if (closed || dealStatus.equals("COMPLETED", ignoreCase = true)) {
                bridgeRepository.deleteNotificationsForChat(chatId)
                removeSellerOffersForCompletedDeal(
                    offerRepository = offerRepository,
                    chatMeta = updatedMeta,
                )
            }
            call.respond(HttpStatusCode.OK)
        }

        get("/{chatId}/messages") {
            val chatId = call.parameters["chatId"].orEmpty()
            call.requireChatParticipant(authVerifier, bridgeRepository, chatId) ?: return@get
            call.respond(bridgeRepository.listChatMessages(chatId))
        }

        post("/{chatId}/messages") {
            val chatId = call.parameters["chatId"].orEmpty()
            val (principal, _) = call.requireChatParticipant(authVerifier, bridgeRepository, chatId) ?: return@post
            val request = call.receive<SendMessageRequest>()
            val text = request.text.trim()
            if (text.isBlank()) throw ValidationException("Message text is required")
            bridgeRepository.sendMessage(
                uid = principal.uid,
                chatId = chatId,
                senderDisplayName = request.senderDisplayName.trim().ifBlank { principal.email ?: principal.uid },
                text = text,
            )
            call.respond(HttpStatusCode.OK)
        }

        delete("/{chatId}") {
            val chatId = call.parameters["chatId"].orEmpty()
            val (principal, meta) = call.requireChatParticipant(authVerifier, bridgeRepository, chatId) ?: return@delete
            val counterpartUid = when (principal.uid) {
                meta.stringOrEmpty("buyerUid") -> meta.stringOrEmpty("sellerUid")
                else -> meta.stringOrEmpty("buyerUid")
            }
            bridgeRepository.deleteThread(
                uid = principal.uid,
                chatId = chatId,
                counterpartUid = counterpartUid,
            )
            call.respond(HttpStatusCode.OK)
        }

        post("/{chatId}/ratings") {
            val chatId = call.parameters["chatId"].orEmpty()
            val (principal, meta) = call.requireChatParticipant(authVerifier, bridgeRepository, chatId) ?: return@post
            val request = call.receive<SubmitRatingRequest>()
            val dealStatus = meta.stringOrEmpty("dealStatus")
            if (!dealStatus.equals("COMPLETED", ignoreCase = true)) {
                throw ValidationException("Trade must be completed before rating")
            }

            val counterpartUid = when (principal.uid) {
                meta.stringOrEmpty("buyerUid") -> meta.stringOrEmpty("sellerUid")
                meta.stringOrEmpty("sellerUid") -> meta.stringOrEmpty("buyerUid")
                else -> ""
            }
            if (counterpartUid.isBlank()) throw ValidationException("Chat counterpart not found")
            if (request.ratedUid.trim() != counterpartUid) {
                throw ForbiddenException("You can only rate the counterpart of this chat")
            }

            val tradeKey = buildTradeRatingKey(chatId = chatId, chatMeta = meta)
            if (bridgeRepository.hasRatedChat(principal.uid, tradeKey)) {
                throw ValidationException("You already rated this trade")
            }

            val now = Clock.System.now().toEpochMilliseconds()
            val normalizedComment = request.comment.trim().take(300)
            val normalizedScore = request.score.coerceIn(1, 5)

            bridgeRepository.saveGivenRating(
                uid = principal.uid,
                chatId = tradeKey,
                payload = buildJsonObject {
                    put("chatId", tradeKey)
                    put("ratedUid", counterpartUid)
                    put("score", normalizedScore)
                    put("comment", normalizedComment)
                    put("createdAt", now)
                },
            )

            bridgeRepository.saveReceivedRating(
                uid = counterpartUid,
                ratingId = "${tradeKey}_${principal.uid}",
                payload = buildJsonObject {
                    put("chatId", tradeKey)
                    put("raterUid", principal.uid)
                    put("score", normalizedScore)
                    put("comment", normalizedComment)
                    put("createdAt", now)
                },
            )

            val received = bridgeRepository.listReceivedRatings(counterpartUid)
            val scores = received.values.mapNotNull { raw ->
                val obj = raw as? JsonObject ?: return@mapNotNull null
                (obj["score"] as? JsonPrimitive)?.content?.toIntOrNull()
            }
            val average = if (scores.isEmpty()) 0.0 else scores.average()
            bridgeRepository.updateUserProfileRating(
                uid = counterpartUid,
                average = average,
                count = scores.size,
            )

            call.respond(HttpStatusCode.OK)
        }
    }
}

private suspend fun ApplicationCall.requireChatParticipant(
    authVerifier: FirebaseAuthVerifier,
    bridgeRepository: PostgresBridgeRepository,
    chatId: String,
): Pair<FirebasePrincipal, JsonObject>? {
    val principal = requireFirebasePrincipal(authVerifier)
    if (chatId.isBlank()) throw ValidationException("Chat id is required")
    val meta = bridgeRepository.getChatMeta(chatId)
    if (meta == null) {
        respond(HttpStatusCode.NotFound)
        return null
    }
    val buyerUid = meta.stringOrEmpty("buyerUid")
    val sellerUid = meta.stringOrEmpty("sellerUid")
    if (principal.uid != buyerUid && principal.uid != sellerUid) {
        throw ForbiddenException("You are not a participant of this chat")
    }
    return principal to meta
}

private fun buildTradeRatingKey(chatId: String, chatMeta: JsonObject): String {
    val closedAt = (chatMeta["closedAt"] as? JsonPrimitive)?.content?.toLongOrNull()
    if (closedAt != null && closedAt > 0L) return "${chatId}_$closedAt"

    val createdAt = (chatMeta["createdAt"] as? JsonPrimitive)?.content?.toLongOrNull()
    if (createdAt != null && createdAt > 0L) return "${chatId}_$createdAt"

    return chatId
}

private fun JsonObject.stringOrEmpty(key: String): String {
    return (this[key] as? JsonPrimitive)?.content?.trim().orEmpty()
}


private suspend fun removeSellerOffersForCompletedDeal(
    offerRepository: OfferRepository,
    chatMeta: JsonObject,
) {
    val sellerUid = chatMeta.stringOrEmpty("sellerUid")
    if (sellerUid.isBlank()) return
    val targetCardId = chatMeta.stringOrEmpty("cardId")
    val targetCardName = chatMeta.stringOrEmpty("cardName")
    val offers = offerRepository.list(cardId = null, userId = sellerUid, type = OfferType.SELL)
    offers.forEach { offer ->
        val matchesCard = when {
            targetCardId.isNotBlank() -> offer.cardId.equals(targetCardId, ignoreCase = true)
            else -> offer.cardName.equals(targetCardName, ignoreCase = true)
        }
        if (matchesCard) {
            offerRepository.deleteOwned(offer.id, sellerUid)
        }
    }
}
