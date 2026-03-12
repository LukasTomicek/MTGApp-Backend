package mtg.app.feature.market.application

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mtg.app.feature.bridge.infrastructure.PostgresBridgeRepository
import mtg.app.feature.offers.domain.Offer
import mtg.app.feature.offers.domain.OfferType
import kotlin.math.PI
import kotlin.math.pow

class MarketVisibilitySupport(
    private val bridgeRepository: PostgresBridgeRepository,
) {
    suspend fun visibleOffersForViewer(
        viewerUid: String,
        offerType: OfferType,
        offers: List<Offer>,
    ): List<Offer> {
        val context = loadContext(viewerUid)
        if (context.viewerPins.isEmpty()) return emptyList()

        return offers.filter { offer ->
            offer.userId != viewerUid &&
                usersHaveIntersectingPinsStrict(
                    viewerPins = context.viewerPins,
                    otherPins = context.pinsByUser[offer.userId].orEmpty(),
                ) &&
                (offerType != OfferType.SELL || sellerMarketKey(offer) !in context.unavailableSellerMarketKeys)
        }
    }

    private suspend fun loadContext(viewerUid: String): VisibilityContext {
        val pinsByUser = parsePinsByUser(bridgeRepository.listMarketplaceMapPinsByUser())
        val viewerPins = pinsByUser[viewerUid].orEmpty()
        if (viewerPins.isEmpty()) {
            return VisibilityContext(
                viewerPins = emptyList(),
                pinsByUser = pinsByUser,
                unavailableSellerMarketKeys = emptySet(),
            )
        }

        val unavailableSellerMarketKeys = bridgeRepository.listChats().values.mapNotNull { raw ->
            val chatRoot = raw as? JsonObject ?: return@mapNotNull null
            val meta = chatRoot["meta"] as? JsonObject ?: return@mapNotNull null
            val status = (meta["dealStatus"] as? JsonPrimitive)?.content.orEmpty()
            if (!status.equals("PROPOSED", ignoreCase = true) &&
                !status.equals("COMPLETED", ignoreCase = true)
            ) {
                return@mapNotNull null
            }
            val sellerUid = (meta["sellerUid"] as? JsonPrimitive)?.content?.trim().orEmpty()
            if (sellerUid.isBlank()) return@mapNotNull null
            val cardId = (meta["cardId"] as? JsonPrimitive)?.content?.trim().orEmpty()
            val cardName = (meta["cardName"] as? JsonPrimitive)?.content?.trim().orEmpty()
            val marketKey = marketKeyOf(cardId = cardId, cardName = cardName)
            "${sellerUid.lowercase()}|$marketKey"
        }.toSet()

        return VisibilityContext(
            viewerPins = viewerPins,
            pinsByUser = pinsByUser,
            unavailableSellerMarketKeys = unavailableSellerMarketKeys,
        )
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
        viewerPins: List<MapPin>,
        otherPins: List<MapPin>,
    ): Boolean {
        if (viewerPins.isEmpty() || otherPins.isEmpty()) return false
        return viewerPins.any { viewerPin ->
            otherPins.any { otherPin ->
                val distanceMeters = haversineMeters(
                    lat1 = viewerPin.latitude,
                    lon1 = viewerPin.longitude,
                    lat2 = otherPin.latitude,
                    lon2 = otherPin.longitude,
                )
                distanceMeters <= viewerPin.radiusMeters + otherPin.radiusMeters
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

    private fun sellerMarketKey(offer: Offer): String {
        return "${offer.userId.lowercase()}|${marketKeyOf(cardId = offer.cardId, cardName = offer.cardName)}"
    }

    private fun marketKeyOf(cardId: String, cardName: String): String {
        return if (cardId.isNotBlank()) {
            "id:${cardId.trim().lowercase()}"
        } else {
            "name:${cardName.trim().lowercase()}"
        }
    }

    private data class VisibilityContext(
        val viewerPins: List<MapPin>,
        val pinsByUser: Map<String, List<MapPin>>,
        val unavailableSellerMarketKeys: Set<String>,
    )

    private data class MapPin(
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Float,
    )
}
