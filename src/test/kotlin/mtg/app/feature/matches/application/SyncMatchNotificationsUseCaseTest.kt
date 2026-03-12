package mtg.app.feature.matches.application

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mtg.app.feature.bridge.infrastructure.ChatArtifactsStore
import mtg.app.feature.bridge.infrastructure.MarketplaceMapPinsStore
import mtg.app.feature.bridge.infrastructure.UserNotificationStore
import mtg.app.feature.offers.domain.Offer
import mtg.app.feature.offers.domain.OfferRepository
import mtg.app.feature.offers.domain.OfferType
import mtg.app.feature.users.application.LoadNicknamesUseCase
import mtg.app.feature.users.domain.UserProfileRepository

class SyncMatchNotificationsUseCaseTest {
    @Test
    fun `returns zero when actor has no pins`() = kotlinx.coroutines.test.runTest {
        val offerRepository = FakeOfferRepository(
            offers = listOf(
                offer("sell-1", "seller", "c1", "Bolt", OfferType.SELL, 2.0),
                offer("buy-1", "buyer", "c1", "Bolt", OfferType.BUY, 2.5),
            )
        )
        val useCase = SyncMatchNotificationsUseCase(
            offerRepository = offerRepository,
            bridgeRepository = FakeMarketplaceMapPinsStore(emptyMap()),
            chatStore = FakeChatArtifactsStore(),
            notificationStore = FakeUserNotificationStore(),
            loadNicknames = LoadNicknamesUseCase(FakeUserProfileRepository()),
        )

        val result = useCase("seller", SyncMatchNotificationsUseCase.MatchSyncType.SELL)

        assertEquals(0, result)
    }

    @Test
    fun `creates seller side match notification for buyer in intersecting radius`() = kotlinx.coroutines.test.runTest {
        val notificationStore = FakeUserNotificationStore()
        val chatStore = FakeChatArtifactsStore()
        val useCase = SyncMatchNotificationsUseCase(
            offerRepository = FakeOfferRepository(
                offers = listOf(
                    offer("sell-1", "seller", "c1", "Bolt", OfferType.SELL, 2.0),
                    offer("buy-1", "buyer", "c1", "Bolt", OfferType.BUY, 2.5),
                )
            ),
            bridgeRepository = FakeMarketplaceMapPinsStore(
                mapOf(
                    "seller" to listOf(pin(50.0, 14.0, 1000f)),
                    "buyer" to listOf(pin(50.0005, 14.0005, 1000f)),
                )
            ),
            chatStore = chatStore,
            notificationStore = notificationStore,
            loadNicknames = LoadNicknamesUseCase(
                FakeUserProfileRepository(
                    nicknames = mapOf(
                        "seller" to "Lukas",
                        "buyer" to "Petr",
                    )
                )
            ),
        )

        val result = useCase("seller", SyncMatchNotificationsUseCase.MatchSyncType.SELL)

        assertEquals(1, result)
        assertEquals(1, chatStore.ensureCalls.size)
        val ensured = chatStore.ensureCalls.single()
        assertEquals("buyer", ensured.buyerUid)
        assertEquals("seller", ensured.sellerUid)
        val notification = notificationStore.notifications.single()
        assertEquals("buyer", notification.uid)
        assertEquals("chat-buyer-seller-c1", (notification.payload["chatId"] as? JsonPrimitive)?.content)
        assertEquals("Lukas", (notification.payload["sellerEmail"] as? JsonPrimitive)?.content)
        assertEquals("Bolt is now in seller's Sell List", (notification.payload["message"] as? JsonPrimitive)?.content)
    }

    @Test
    fun `does not notify when pins do not intersect`() = kotlinx.coroutines.test.runTest {
        val notificationStore = FakeUserNotificationStore()
        val chatStore = FakeChatArtifactsStore()
        val useCase = SyncMatchNotificationsUseCase(
            offerRepository = FakeOfferRepository(
                offers = listOf(
                    offer("buy-1", "buyer", "c1", "Bolt", OfferType.BUY, 2.5),
                    offer("sell-1", "seller", "c1", "Bolt", OfferType.SELL, 2.0),
                )
            ),
            bridgeRepository = FakeMarketplaceMapPinsStore(
                mapOf(
                    "buyer" to listOf(pin(50.0, 14.0, 500f)),
                    "seller" to listOf(pin(51.0, 15.0, 500f)),
                )
            ),
            chatStore = chatStore,
            notificationStore = notificationStore,
            loadNicknames = LoadNicknamesUseCase(FakeUserProfileRepository()),
        )

        val result = useCase("buyer", SyncMatchNotificationsUseCase.MatchSyncType.BUY)

        assertEquals(0, result)
        assertTrue(chatStore.ensureCalls.isEmpty())
        assertTrue(notificationStore.notifications.isEmpty())
    }

    private fun offer(
        id: String,
        userId: String,
        cardId: String,
        cardName: String,
        type: OfferType,
        price: Double?,
    ) = Offer(
        id = id,
        userId = userId,
        cardId = cardId,
        cardName = cardName,
        cardTypeLine = null,
        cardImageUrl = null,
        type = type,
        price = price,
        createdAt = 1L,
    )

    private fun pin(latitude: Double, longitude: Double, radiusMeters: Float): JsonObject = buildJsonObject {
        put("latitude", latitude)
        put("longitude", longitude)
        put("radiusMeters", radiusMeters)
    }

    private class FakeOfferRepository(
        private val offers: List<Offer>,
    ) : OfferRepository {
        override suspend fun create(offer: Offer): Offer = offer

        override suspend fun list(cardId: String?, userId: String?, type: OfferType?): List<Offer> {
            return offers.filter { offer ->
                (cardId == null || offer.cardId == cardId) &&
                    (userId == null || offer.userId == userId) &&
                    (type == null || offer.type == type)
            }
        }

        override suspend fun deleteOwned(id: String, ownerUserId: String): Boolean = false
    }

    private class FakeMarketplaceMapPinsStore(
        private val pinsByUser: Map<String, List<JsonObject>>,
    ) : MarketplaceMapPinsStore {
        override fun listMarketplaceMapPinsByUser(): JsonObject {
            return JsonObject(
                pinsByUser.mapValues { (_, pins) ->
                    JsonObject(pins.mapIndexed { index, pin -> "pin-$index" to pin }.toMap())
                }
            )
        }
    }

    private class FakeChatArtifactsStore : ChatArtifactsStore {
        data class EnsureCall(
            val buyerUid: String,
            val buyerEmail: String,
            val sellerUid: String,
            val sellerEmail: String,
            val cardId: String,
            val cardName: String,
        )

        val ensureCalls = mutableListOf<EnsureCall>()

        override fun listChats(): JsonObject = JsonObject(emptyMap())

        override fun ensureChatAndArtifacts(
            buyerUid: String,
            buyerEmail: String,
            sellerUid: String,
            sellerEmail: String,
            cardId: String,
            cardName: String,
        ): String {
            ensureCalls += EnsureCall(buyerUid, buyerEmail, sellerUid, sellerEmail, cardId, cardName)
            return "chat-${buyerUid}-${sellerUid}-${cardId.ifBlank { cardName }}"
        }
    }

    private class FakeUserNotificationStore : UserNotificationStore {
        data class NotificationCall(
            val uid: String,
            val notificationId: String,
            val payload: JsonObject,
        )

        val notifications = mutableListOf<NotificationCall>()

        override fun upsertNotification(uid: String, notificationId: String, payload: JsonObject) {
            notifications += NotificationCall(uid, notificationId, payload)
        }

        override fun deleteNotificationsForChat(chatId: String) = Unit
    }

    private class FakeUserProfileRepository(
        private val nicknames: Map<String, String> = emptyMap(),
    ) : UserProfileRepository {
        override suspend fun upsertNickname(userId: String, nickname: String) = Unit
        override suspend fun loadNickname(userId: String): String? = nicknames[userId]
        override suspend fun loadNicknames(userIds: Set<String>): Map<String, String> =
            nicknames.filterKeys { it in userIds }
    }
}
