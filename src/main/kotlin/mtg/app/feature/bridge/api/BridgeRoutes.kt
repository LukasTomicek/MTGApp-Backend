package mtg.app.feature.bridge.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mtg.app.feature.bridge.infrastructure.PostgresBridgeRepository

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
    val uid: String,
    val senderDisplayName: String,
    val text: String,
)

@Serializable
private data class UpdateProfileRatingRequest(
    val average: Double,
    val count: Int,
)

@Serializable
private data class UpdateOnboardingRequest(
    val completed: Boolean,
)

fun Route.registerBridgeRoutes(
    bridgeRepository: PostgresBridgeRepository,
) {
    route("/v1/bridge") {
        post("/chat/ensure") {
            val request = call.receive<EnsureChatRequest>()
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

        route("/market") {
            get("/sell-offers/{uid}") {
                val uid = call.parameters["uid"].orEmpty()
                call.respond(bridgeRepository.listMarketplaceSellEntriesByUser(uid))
            }

            get("/sell-offers") {
                call.respond(bridgeRepository.listMarketplaceSellEntriesByUsers())
            }

            get("/buy-offers") {
                call.respond(bridgeRepository.listMarketplaceBuyEntriesByUsers())
            }

            get("/map-pins") {
                call.respond(bridgeRepository.listMarketplaceMapPinsByUser())
            }
        }

        get("/chats") {
            call.respond(bridgeRepository.listChats())
        }

        get("/chats/{chatId}/meta") {
            val chatId = call.parameters["chatId"].orEmpty()
            val meta = bridgeRepository.getChatMeta(chatId)
            if (meta == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(meta)
            }
        }

        put("/chats/{chatId}/meta") {
            val chatId = call.parameters["chatId"].orEmpty()
            val payload = call.receive<JsonObject>()
            bridgeRepository.upsertChatMeta(chatId = chatId, payload = payload)
            call.respond(HttpStatusCode.OK)
        }

        patch("/chats/{chatId}/meta") {
            val chatId = call.parameters["chatId"].orEmpty()
            val patch = call.receive<JsonObject>()
            bridgeRepository.patchChatMeta(chatId = chatId, patch = patch)
            call.respond(HttpStatusCode.OK)
        }

        delete("/chats/{chatId}/meta") {
            val chatId = call.parameters["chatId"].orEmpty()
            bridgeRepository.deleteChatMeta(chatId)
            call.respond(HttpStatusCode.OK)
        }

        get("/chats/{chatId}/messages") {
            val chatId = call.parameters["chatId"].orEmpty()
            call.respond(bridgeRepository.listChatMessages(chatId))
        }

        put("/chats/{chatId}/messages/{messageId}") {
            val chatId = call.parameters["chatId"].orEmpty()
            val messageId = call.parameters["messageId"].orEmpty()
            val payload = call.receive<JsonObject>()
            bridgeRepository.upsertChatMessage(
                chatId = chatId,
                messageId = messageId,
                payload = payload,
            )
            call.respond(HttpStatusCode.OK)
        }

        post("/chats/{chatId}/messages/send") {
            val chatId = call.parameters["chatId"].orEmpty()
            val request = call.receive<SendMessageRequest>()
            bridgeRepository.sendMessage(
                uid = request.uid,
                chatId = chatId,
                senderDisplayName = request.senderDisplayName,
                text = request.text,
            )
            call.respond(HttpStatusCode.OK)
        }

        delete("/chats/{chatId}/messages") {
            val chatId = call.parameters["chatId"].orEmpty()
            bridgeRepository.deleteChatMessages(chatId)
            call.respond(HttpStatusCode.OK)
        }

        delete("/chats/{chatId}/thread") {
            val chatId = call.parameters["chatId"].orEmpty()
            val uid = call.request.queryParameters["uid"].orEmpty()
            val counterpartUid = call.request.queryParameters["counterpartUid"].orEmpty()
            bridgeRepository.deleteThread(uid = uid, chatId = chatId, counterpartUid = counterpartUid)
            call.respond(HttpStatusCode.OK)
        }

        route("/users/{uid}") {
            get("/collection") {
                val uid = call.parameters["uid"].orEmpty()
                call.respond(bridgeRepository.listUserCollection(uid))
            }

            put("/collection/{entryId}") {
                val uid = call.parameters["uid"].orEmpty()
                val entryId = call.parameters["entryId"].orEmpty()
                val payload = call.receive<JsonObject>()
                bridgeRepository.upsertUserCollectionEntry(uid = uid, entryId = entryId, payload = payload)
                call.respond(HttpStatusCode.OK)
            }

            delete("/collection/{entryId}") {
                val uid = call.parameters["uid"].orEmpty()
                val entryId = call.parameters["entryId"].orEmpty()
                bridgeRepository.deleteUserCollectionEntry(uid = uid, entryId = entryId)
                call.respond(HttpStatusCode.OK)
            }

            delete("/collection") {
                val uid = call.parameters["uid"].orEmpty()
                bridgeRepository.clearUserCollection(uid)
                call.respond(HttpStatusCode.OK)
            }

            get("/map-pins") {
                val uid = call.parameters["uid"].orEmpty()
                call.respond(bridgeRepository.listUserMapPins(uid))
            }

            put("/map-pins") {
                val uid = call.parameters["uid"].orEmpty()
                val payload = call.receive<JsonObject>()
                bridgeRepository.replaceUserMapPins(uid = uid, payload = payload)
                call.respond(HttpStatusCode.OK)
            }

            get("/matches") {
                val uid = call.parameters["uid"].orEmpty()
                call.respond(bridgeRepository.listUserMatches(uid))
            }

            put("/matches/{chatId}") {
                val uid = call.parameters["uid"].orEmpty()
                val chatId = call.parameters["chatId"].orEmpty()
                val payload = call.receive<JsonObject>()
                bridgeRepository.upsertUserMatch(uid = uid, chatId = chatId, payload = payload)
                call.respond(HttpStatusCode.OK)
            }

            delete("/matches/{chatId}") {
                val uid = call.parameters["uid"].orEmpty()
                val chatId = call.parameters["chatId"].orEmpty()
                bridgeRepository.deleteUserMatch(uid = uid, chatId = chatId)
                call.respond(HttpStatusCode.OK)
            }

            get("/notifications") {
                val uid = call.parameters["uid"].orEmpty()
                call.respond(bridgeRepository.listNotifications(uid))
            }

            put("/notifications/{notificationId}") {
                val uid = call.parameters["uid"].orEmpty()
                val notificationId = call.parameters["notificationId"].orEmpty()
                val payload = call.receive<JsonObject>()
                bridgeRepository.upsertNotification(uid = uid, notificationId = notificationId, payload = payload)
                call.respond(HttpStatusCode.OK)
            }

            patch("/notifications/{notificationId}") {
                val uid = call.parameters["uid"].orEmpty()
                val notificationId = call.parameters["notificationId"].orEmpty()
                bridgeRepository.markNotificationRead(uid = uid, notificationId = notificationId)
                call.respond(HttpStatusCode.OK)
            }

            delete("/notifications/{notificationId}") {
                val uid = call.parameters["uid"].orEmpty()
                val notificationId = call.parameters["notificationId"].orEmpty()
                bridgeRepository.deleteNotification(uid = uid, notificationId = notificationId)
                call.respond(HttpStatusCode.OK)
            }

            get("/notifications/unread") {
                val uid = call.parameters["uid"].orEmpty()
                call.respond(
                    buildJsonObject {
                        put("hasUnread", bridgeRepository.hasUnreadNotifications(uid))
                    }
                )
            }

            get("/ratings/given/{chatId}") {
                val uid = call.parameters["uid"].orEmpty()
                val chatId = call.parameters["chatId"].orEmpty()
                call.respond(buildJsonObject { put("exists", bridgeRepository.hasRatedChat(uid, chatId)) })
            }

            put("/ratings/given/{chatId}") {
                val uid = call.parameters["uid"].orEmpty()
                val chatId = call.parameters["chatId"].orEmpty()
                val payload = call.receive<JsonObject>()
                bridgeRepository.saveGivenRating(uid = uid, chatId = chatId, payload = payload)
                call.respond(HttpStatusCode.OK)
            }

            get("/ratings/received") {
                val uid = call.parameters["uid"].orEmpty()
                call.respond(bridgeRepository.listReceivedRatings(uid))
            }

            put("/ratings/received/{ratingId}") {
                val uid = call.parameters["uid"].orEmpty()
                val ratingId = call.parameters["ratingId"].orEmpty()
                val payload = call.receive<JsonObject>()
                bridgeRepository.saveReceivedRating(uid = uid, ratingId = ratingId, payload = payload)
                call.respond(HttpStatusCode.OK)
            }

            put("/profile/rating") {
                val uid = call.parameters["uid"].orEmpty()
                val request = call.receive<UpdateProfileRatingRequest>()
                bridgeRepository.updateUserProfileRating(
                    uid = uid,
                    average = request.average,
                    count = request.count,
                )
                call.respond(HttpStatusCode.OK)
            }

            get("/profile/onboarding") {
                val uid = call.parameters["uid"].orEmpty()
                call.respond(buildJsonObject { put("completed", bridgeRepository.loadOnboardingCompleted(uid)) })
            }

            put("/profile/onboarding") {
                val uid = call.parameters["uid"].orEmpty()
                val request = call.receive<UpdateOnboardingRequest>()
                bridgeRepository.updateOnboardingCompleted(uid = uid, completed = request.completed)
                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
