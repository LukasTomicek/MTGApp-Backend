package mtg.app.feature.bridge.infrastructure

import kotlinx.serialization.json.JsonObject

interface MarketplaceMapPinsStore {
    fun listMarketplaceMapPinsByUser(): JsonObject
}
