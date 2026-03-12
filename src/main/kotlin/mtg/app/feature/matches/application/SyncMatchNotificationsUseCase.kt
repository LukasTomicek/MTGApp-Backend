package mtg.app.feature.matches.application

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mtg.app.feature.bridge.infrastructure.PostgresBridgeRepository
import mtg.app.feature.bridge.infrastructure.PostgresChatStore
import mtg.app.feature.bridge.infrastructure.PostgresNotificationStore
import mtg.app.feature.offers.domain.Offer
import mtg.app.feature.offers.domain.OfferRepository
import mtg.app.feature.offers.domain.OfferType
import mtg.app.feature.users.application.LoadNicknamesUseCase
import kotlin.math.PI
import kotlin.math.pow

class SyncMatchNotificationsUseCase(
    private val offerRepository: OfferRepository,
    private val bridgeRepository: PostgresBridgeRepository,
    private val chatStore: PostgresChatStore,
    private val notificationStore: PostgresNotificationStore,
    private val loadNicknames: LoadNicknamesUseCase,
) {
    suspend operator fun invoke(userId: String, type: MatchSyncType): Int {
        val normalizedUserId = userId.trim()
        if (normalizedUserId.isBlank()) return 0

        val actorSellOffers = if (type != MatchSyncType.BUY) {
            offerRepository.list(cardId = null, userId = normalizedUserId, type = OfferType.SELL)
        } else {
            emptyList()
        }
        val actorBuyOffers = if (type != MatchSyncType.SELL) {
            offerRepository.list(cardId = null, userId = normalizedUserId, type = OfferType.BUY)
        } else {
            emptyList()
        }

        if (actorSellOffers.isEmpty() && actorBuyOffers.isEmpty()) return 0

        val pinsByUser = parsePinsByUser(bridgeRepository.listMarketplaceMapPinsByUser())
        val actorPins = pinsByUser[normalizedUserId].orEmpty()
        if (actorPins.isEmpty()) return 0

        val candidateBuyOffers = if (actorSellOffers.isNotEmpty()) {
            offerRepository.list(cardId = null, userId = null, type = OfferType.BUY)
                .filter { it.userId != normalizedUserId }
        } else {
            emptyList()
        }
        val candidateSellOffers = if (actorBuyOffers.isNotEmpty()) {
            offerRepository.list(cardId = null, userId = null, type = OfferType.SELL)
                .filter { it.userId != normalizedUserId }
        } else {
            emptyList()
        }

        val nicknames = loadNicknames(
            buildSet {
                add(normalizedUserId)
                candidateBuyOffers.forEach { add(it.userId) }
                candidateSellOffers.forEach { add(it.userId) }
            }
        )
        val actorDisplayName = nicknames[normalizedUserId]?.trim().orEmpty().ifBlank { normalizedUserId }

        var notificationsUpserted = 0

        if (actorSellOffers.isNotEmpty()) {
            val buyOffersByUser = candidateBuyOffers.groupBy { it.userId }
            buyOffersByUser.forEach { (buyerUid, buyOffers) ->
                val buyerPins = pinsByUser[buyerUid].orEmpty()
                if (!usersHaveIntersectingPinsStrict(actorPins, buyerPins)) return@forEach

                val buyOffersByKey = buyOffers.associateBy { it.marketKey() }
                actorSellOffers.forEach { sellOffer ->
                    val matchingBuyOffer = buyOffersByKey[sellOffer.marketKey()] ?: return@forEach
                    val cardName = sellOffer.cardName.ifBlank { "Unknown card" }
                    val buyerDisplayName = nicknames[buyerUid]?.trim().orEmpty().ifBlank { buyerUid }
                    val chatId = chatStore.ensureChatAndArtifacts(
                        buyerUid = buyerUid,
                        buyerEmail = buyerDisplayName,
                        sellerUid = normalizedUserId,
                        sellerEmail = actorDisplayName,
                        cardId = sellOffer.cardId,
                        cardName = cardName,
                    )
                    val notificationId = buildSellNotificationId(
                        sellerUid = normalizedUserId,
                        sellEntryId = sellOffer.id,
                    )
                    notificationStore.upsertNotification(
                        uid = buyerUid,
                        notificationId = notificationId,
                        payload = buildJsonObject {
                            put("notificationId", notificationId)
                            put("chatId", chatId)
                            put("sellerUid", normalizedUserId)
                            put("sellerEmail", actorDisplayName)
                            put("sellerImageUrl", "")
                            put("cardId", sellOffer.cardId)
                            put("cardName", cardName)
                            matchingBuyOffer.price?.let { put("price", it) }
                            put("message", "$cardName is now in seller's Sell List")
                            put("isRead", false)
                        }
                    )
                    notificationsUpserted += 1
                }
            }
        }

        if (actorBuyOffers.isNotEmpty()) {
            val sellOffersByUser = candidateSellOffers.groupBy { it.userId }
            sellOffersByUser.forEach { (sellerUid, sellOffers) ->
                val sellerPins = pinsByUser[sellerUid].orEmpty()
                if (!usersHaveIntersectingPinsStrict(actorPins, sellerPins)) return@forEach

                val sellOffersByKey = sellOffers.associateBy { it.marketKey() }
                actorBuyOffers.forEach { buyOffer ->
                    val matchingSellOffer = sellOffersByKey[buyOffer.marketKey()] ?: return@forEach
                    val cardName = matchingSellOffer.cardName.ifBlank { "Unknown card" }
                    val sellerDisplayName = nicknames[sellerUid]?.trim().orEmpty().ifBlank { sellerUid }
                    val chatId = chatStore.ensureChatAndArtifacts(
                        buyerUid = normalizedUserId,
                        buyerEmail = actorDisplayName,
                        sellerUid = sellerUid,
                        sellerEmail = sellerDisplayName,
                        cardId = matchingSellOffer.cardId,
                        cardName = cardName,
                    )
                    val notificationId = buildBuyNotificationId(
                        buyerUid = normalizedUserId,
                        buyEntryId = buyOffer.id,
                    )
                    notificationStore.upsertNotification(
                        uid = sellerUid,
                        notificationId = notificationId,
                        payload = buildJsonObject {
                            put("notificationId", notificationId)
                            put("chatId", chatId)
                            put("sellerUid", normalizedUserId)
                            put("sellerEmail", actorDisplayName)
                            put("sellerImageUrl", "")
                            put("cardId", matchingSellOffer.cardId)
                            put("cardName", cardName)
                            buyOffer.price?.let { put("price", it) }
                            put("message", "$actorDisplayName wants to buy $cardName")
                            put("isRead", false)
                        }
                    )
                    notificationsUpserted += 1
                }
            }
        }

        return notificationsUpserted
    }

    enum class MatchSyncType {
        BUY,
        SELL,
        ALL,
        ;

        companion object {
            fun fromRaw(raw: String?): MatchSyncType {
                return entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: ALL
            }
        }
    }

    private fun parsePinsByUser(raw: JsonObject): Map<String, List<MapPin>> {
        return raw.mapValues { (_, value) ->
            val pins = value as? JsonObject ?: return@mapValues emptyList()
            pins.values.mapNotNull { pinValue ->
                val pin = pinValue as? JsonObject ?: return@mapNotNull null
                val latitude = (pin["latitude"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: return@mapNotNull null
                val longitude = (pin["longitude"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: return@mapNotNull null
                val radiusMeters = (pin["radiusMeters"] as? JsonPrimitive)?.content?.toFloatOrNull() ?: return@mapNotNull null
                MapPin(
                    latitude = latitude,
                    longitude = longitude,
                    radiusMeters = radiusMeters,
                )
            }
        }
    }

    private fun usersHaveIntersectingPinsStrict(
        firstPins: List<MapPin>,
        secondPins: List<MapPin>,
    ): Boolean {
        if (firstPins.isEmpty() || secondPins.isEmpty()) return false
        return firstPins.any { firstPin ->
            secondPins.any { secondPin ->
                val distanceMeters = haversineMeters(
                    lat1 = firstPin.latitude,
                    lon1 = firstPin.longitude,
                    lat2 = secondPin.latitude,
                    lon2 = secondPin.longitude,
                )
                distanceMeters <= firstPin.radiusMeters + secondPin.radiusMeters
            }
        }
    }

    private fun haversineMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Float {
        val earthRadius = 6_371_000.0
        val dLat = (lat2 - lat1) * PI / 180.0
        val dLon = (lon2 - lon1) * PI / 180.0
        val a = kotlin.math.sin(dLat / 2).pow(2.0) +
            kotlin.math.cos(lat1 * PI / 180.0) *
            kotlin.math.cos(lat2 * PI / 180.0) *
            kotlin.math.sin(dLon / 2).pow(2.0)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return (earthRadius * c).toFloat()
    }

    private fun Offer.marketKey(): String {
        return if (cardId.isNotBlank()) {
            "id:${cardId.trim().lowercase()}"
        } else {
            "name:${cardName.trim().lowercase()}"
        }
    }

    private fun buildSellNotificationId(sellerUid: String, sellEntryId: String): String {
        return "sell_match_${sellerUid}_${sanitizeKeySegment(sellEntryId)}"
    }

    private fun buildBuyNotificationId(buyerUid: String, buyEntryId: String): String {
        return "buy_match_${buyerUid}_${sanitizeKeySegment(buyEntryId)}"
    }

    private fun sanitizeKeySegment(value: String): String {
        return value.lowercase()
            .replace(":", "_")
            .replace("/", "_")
            .replace(".", "_")
    }

    private data class MapPin(
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Float,
    )
}
