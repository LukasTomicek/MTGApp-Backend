package mtg.app.feature.market.application

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mtg.app.feature.bridge.infrastructure.ChatArtifactsStore
import mtg.app.feature.bridge.infrastructure.MarketplaceMapPinsStore
import mtg.app.feature.offers.domain.Offer
import mtg.app.feature.offers.domain.OfferType

class MarketVisibilitySupportTest {
    @Test
    fun `returns empty when viewer has no pins`() = kotlinx.coroutines.test.runTest {
        val support = MarketVisibilitySupport(
            bridgeRepository = FakeMarketplaceMapPinsStore(emptyMap()),
            chatStore = FakeChatArtifactsStore(JsonObject(emptyMap())),
        )

        val visible = support.visibleOffersForViewer(
            viewerUid = "viewer",
            offerType = OfferType.SELL,
            offers = listOf(offer("offer-1", "seller", "c1", "Bolt", OfferType.SELL)),
        )

        assertEquals(emptyList(), visible)
    }

    @Test
    fun `filters own offers and offers outside intersecting radius`() = kotlinx.coroutines.test.runTest {
        val support = MarketVisibilitySupport(
            bridgeRepository = FakeMarketplaceMapPinsStore(
                mapOf(
                    "viewer" to listOf(pin(50.0, 14.0, 1000f)),
                    "nearby" to listOf(pin(50.0005, 14.0005, 1000f)),
                    "far" to listOf(pin(51.0, 15.0, 1000f)),
                )
            ),
            chatStore = FakeChatArtifactsStore(JsonObject(emptyMap())),
        )

        val visible = support.visibleOffersForViewer(
            viewerUid = "viewer",
            offerType = OfferType.SELL,
            offers = listOf(
                offer("offer-1", "viewer", "c1", "Bolt", OfferType.SELL),
                offer("offer-2", "nearby", "c2", "Drain", OfferType.SELL),
                offer("offer-3", "far", "c3", "Counterspell", OfferType.SELL),
            ),
        )

        assertEquals(listOf("offer-2"), visible.map { it.id })
    }

    @Test
    fun `hides seller offer when matching proposed or completed chat exists`() = kotlinx.coroutines.test.runTest {
        val support = MarketVisibilitySupport(
            bridgeRepository = FakeMarketplaceMapPinsStore(
                mapOf(
                    "viewer" to listOf(pin(50.0, 14.0, 1000f)),
                    "seller" to listOf(pin(50.0005, 14.0005, 1000f)),
                )
            ),
            chatStore = FakeChatArtifactsStore(
                JsonObject(
                    mapOf(
                        "chat-1" to buildJsonObject {
                            put("meta", buildJsonObject {
                                put("sellerUid", "seller")
                                put("cardId", "c1")
                                put("cardName", "Bolt")
                                put("dealStatus", "PROPOSED")
                            })
                        }
                    )
                )
            ),
        )

        val visible = support.visibleOffersForViewer(
            viewerUid = "viewer",
            offerType = OfferType.SELL,
            offers = listOf(
                offer("offer-1", "seller", "c1", "Bolt", OfferType.SELL),
                offer("offer-2", "seller", "c2", "Drain", OfferType.SELL),
            ),
        )

        assertEquals(listOf("offer-2"), visible.map { it.id })
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

    private fun pin(latitude: Double, longitude: Double, radiusMeters: Float): JsonObject = buildJsonObject {
        put("latitude", latitude)
        put("longitude", longitude)
        put("radiusMeters", radiusMeters)
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

    private class FakeChatArtifactsStore(
        private val chats: JsonObject,
    ) : ChatArtifactsStore {
        override fun listChats(): JsonObject = chats

        override fun ensureChatAndArtifacts(
            buyerUid: String,
            buyerEmail: String,
            sellerUid: String,
            sellerEmail: String,
            cardId: String,
            cardName: String,
        ): String = error("Not used in this test")
    }
}
