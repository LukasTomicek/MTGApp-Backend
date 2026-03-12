package mtg.app.feature.chat.api

import io.ktor.client.request.delete
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mtg.app.core.auth.FirebaseAuthVerifier
import mtg.app.core.auth.FirebasePrincipal
import mtg.app.core.plugins.configureSerialization
import mtg.app.core.plugins.configureStatusPages
import mtg.app.feature.bridge.infrastructure.ChatRouteStore
import mtg.app.feature.bridge.infrastructure.TradeRatingStore
import mtg.app.feature.bridge.infrastructure.UserNotificationStore
import mtg.app.feature.offers.domain.Offer
import mtg.app.feature.offers.domain.OfferRepository
import mtg.app.feature.offers.domain.OfferType

class ChatRoutesTest {
    @Test
    fun `patch completed chat removes notifications and seller offers`() = testApplication {
        val chatStore = FakeChatRouteStore(
            metas = mutableMapOf(
                "chat-1" to chatMeta(dealStatus = "OPEN", sellerUid = "seller", buyerUid = "buyer", cardId = "c1", cardName = "Bolt")
            )
        )
        val notificationStore = FakeNotificationStore()
        val ratingStore = FakeRatingStore()
        val offerRepository = FakeOfferRepository(
            offers = mutableListOf(
                offer(id = "sell-1", userId = "seller", cardId = "c1", cardName = "Bolt", type = OfferType.SELL),
                offer(id = "sell-2", userId = "seller", cardId = "c2", cardName = "Drain", type = OfferType.SELL),
            )
        )

        application {
            configureSerialization()
            configureStatusPages()
            routing {
                registerChatRoutes(
                    authVerifier = FirebaseAuthVerifier("test-project"),
                    chatStore = chatStore,
                    notificationStore = notificationStore,
                    ratingStore = ratingStore,
                    offerRepository = offerRepository,
                    authPrincipalResolver = { FirebasePrincipal(uid = "buyer", email = "buyer@test") },
                )
            }
        }

        val response = client.patch("/v1/chats/chat-1") {
            contentType(ContentType.Application.Json)
            setBody("""{"dealStatus":"COMPLETED","closed":true}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(listOf("chat-1"), notificationStore.deletedChats)
        assertEquals(listOf("sell-1"), offerRepository.deletedIds)
        assertEquals(listOf("sell-2"), offerRepository.offers.map { it.id })
    }

    @Test
    fun `rating rejects wrong counterpart and deletes thread after second rating`() = testApplication {
        val chatStore = FakeChatRouteStore(
            metas = mutableMapOf(
                "chat-1" to chatMeta(
                    dealStatus = "COMPLETED",
                    sellerUid = "seller",
                    buyerUid = "buyer",
                    cardId = "c1",
                    cardName = "Bolt",
                    closedAt = 123L,
                )
            )
        )
        val notificationStore = FakeNotificationStore()
        val ratingStore = FakeRatingStore(
            ratedTradeKeysByUser = mutableMapOf("seller" to mutableSetOf("chat-1_123"))
        )

        application {
            configureSerialization()
            configureStatusPages()
            routing {
                registerChatRoutes(
                    authVerifier = FirebaseAuthVerifier("test-project"),
                    chatStore = chatStore,
                    notificationStore = notificationStore,
                    ratingStore = ratingStore,
                    offerRepository = FakeOfferRepository(),
                    authPrincipalResolver = {
                        val uid = request.headers[HttpHeaders.Authorization].orEmpty().removePrefix("Test ")
                        FirebasePrincipal(uid = uid, email = "$uid@test")
                    },
                )
            }
        }

        val forbidden = client.post("/v1/chats/chat-1/ratings") {
            headers.append(HttpHeaders.Authorization, "Test buyer")
            contentType(ContentType.Application.Json)
            setBody("""{"ratedUid":"someone-else","score":5,"comment":"ok"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, forbidden.status)

        val ok = client.post("/v1/chats/chat-1/ratings") {
            headers.append(HttpHeaders.Authorization, "Test buyer")
            contentType(ContentType.Application.Json)
            setBody("""{"ratedUid":"seller","score":5,"comment":"great"}""")
        }
        assertEquals(HttpStatusCode.OK, ok.status)
        assertEquals(listOf("chat-1_123"), ratingStore.savedGivenTradeKeys)
        assertEquals(listOf("seller"), ratingStore.updatedProfileUids)
        assertEquals(listOf(DeleteThreadCall(uid = "buyer", chatId = "chat-1", counterpartUid = "seller")), chatStore.deletedThreads)
    }

    private fun chatMeta(
        dealStatus: String,
        sellerUid: String,
        buyerUid: String,
        cardId: String,
        cardName: String,
        closedAt: Long? = null,
    ): JsonObject = buildJsonObject {
        put("dealStatus", dealStatus)
        put("sellerUid", sellerUid)
        put("buyerUid", buyerUid)
        put("cardId", cardId)
        put("cardName", cardName)
        closedAt?.let { put("closedAt", it) }
    }

    private fun offer(id: String, userId: String, cardId: String, cardName: String, type: OfferType) = Offer(
        id = id,
        userId = userId,
        cardId = cardId,
        cardName = cardName,
        cardTypeLine = null,
        cardImageUrl = null,
        type = type,
        price = null,
        createdAt = 1L,
    )

    private data class DeleteThreadCall(val uid: String, val chatId: String, val counterpartUid: String)

    private class FakeChatRouteStore(
        val metas: MutableMap<String, JsonObject> = mutableMapOf(),
        val messages: MutableMap<String, JsonObject> = mutableMapOf(),
    ) : ChatRouteStore {
        val deletedThreads = mutableListOf<DeleteThreadCall>()

        override fun listChats(): JsonObject = JsonObject(metas.mapValues { (_, meta) -> buildJsonObject { put("meta", meta) } })
        override fun getChatMeta(chatId: String): JsonObject? = metas[chatId]
        override fun patchChatMeta(chatId: String, patch: JsonObject) {
            val current = metas[chatId] ?: JsonObject(emptyMap())
            metas[chatId] = buildJsonObject {
                current.forEach { (key, value) -> put(key, value) }
                patch.forEach { (key, value) -> put(key, value) }
            }
        }
        override fun listChatMessages(chatId: String): JsonObject = messages[chatId] ?: JsonObject(emptyMap())
        override fun sendMessage(uid: String, chatId: String, senderDisplayName: String, text: String) = Unit
        override fun deleteThread(uid: String, chatId: String, counterpartUid: String) {
            deletedThreads += DeleteThreadCall(uid, chatId, counterpartUid)
        }
        override fun ensureChatAndArtifacts(buyerUid: String, buyerEmail: String, sellerUid: String, sellerEmail: String, cardId: String, cardName: String): String = "chat-ensure"
    }

    private class FakeNotificationStore : UserNotificationStore {
        val deletedChats = mutableListOf<String>()
        override fun upsertNotification(uid: String, notificationId: String, payload: JsonObject) = Unit
        override fun deleteNotificationsForChat(chatId: String) {
            deletedChats += chatId
        }
    }

    private class FakeRatingStore(
        private val ratedTradeKeysByUser: MutableMap<String, MutableSet<String>> = mutableMapOf(),
    ) : TradeRatingStore {
        val savedGivenTradeKeys = mutableListOf<String>()
        val updatedProfileUids = mutableListOf<String>()
        private val receivedByUid = mutableMapOf<String, MutableMap<String, JsonElement>>()

        override fun hasRatedChat(uid: String, chatId: String): Boolean {
            return chatId in ratedTradeKeysByUser[uid].orEmpty()
        }

        override fun listReceivedRatings(uid: String): JsonObject {
            return JsonObject(receivedByUid[uid].orEmpty())
        }

        override fun saveGivenRating(uid: String, chatId: String, payload: JsonObject) {
            savedGivenTradeKeys += chatId
            ratedTradeKeysByUser.getOrPut(uid) { mutableSetOf() }.add(chatId)
        }

        override fun saveReceivedRating(uid: String, ratingId: String, payload: JsonObject) {
            receivedByUid.getOrPut(uid) { linkedMapOf() }[ratingId] = payload
        }

        override fun updateUserProfileRating(uid: String, average: Double, count: Int) {
            updatedProfileUids += uid
        }
    }

    private class FakeOfferRepository(
        val offers: MutableList<Offer> = mutableListOf(),
    ) : OfferRepository {
        val deletedIds = mutableListOf<String>()
        override suspend fun create(offer: Offer): Offer = offer
        override suspend fun list(cardId: String?, userId: String?, type: OfferType?): List<Offer> {
            return offers.filter { offer ->
                (cardId == null || offer.cardId == cardId) &&
                    (userId == null || offer.userId == userId) &&
                    (type == null || offer.type == type)
            }
        }
        override suspend fun deleteOwned(id: String, ownerUserId: String): Boolean {
            val removed = offers.removeIf { it.id == id && it.userId == ownerUserId }
            if (removed) deletedIds += id
            return removed
        }
    }
}
