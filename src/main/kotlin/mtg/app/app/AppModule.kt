package mtg.app.app

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.routing.routing
import mtg.app.core.plugins.configureMonitoring
import mtg.app.core.plugins.configureSerialization
import mtg.app.core.plugins.configureStatusPages
import mtg.app.feature.bridge.api.registerBridgeRoutes
import mtg.app.feature.health.api.registerHealthRoutes
import mtg.app.feature.market.api.registerMarketRoutes
import mtg.app.feature.matches.api.registerMatchRoutes
import mtg.app.feature.offers.api.registerOfferRoutes
import mtg.app.feature.users.api.registerUserProfileRoutes

fun Application.configureApp() {
    configureSerialization()
    configureMonitoring()
    configureStatusPages()

    val dependencies = AppDependencies(config = environment.config)
    monitor.subscribe(ApplicationStopped) {
        dependencies.close()
    }

    routing {
        registerHealthRoutes(getHealthStatus = dependencies.getHealthStatus)

        registerOfferRoutes(
            createOffer = dependencies.createOffer,
            listOffers = dependencies.listOffers,
            deleteOffer = dependencies.deleteOffer,
        )

        registerMarketRoutes(
            loadMarketCards = dependencies.loadMarketCards,
            loadMarketSellers = dependencies.loadMarketSellers,
        )

        registerMatchRoutes(
            loadMatches = dependencies.loadMatches,
        )

        registerUserProfileRoutes(
            saveUserNickname = dependencies.saveUserNickname,
            loadUserNickname = dependencies.loadUserNickname,
        )

        registerBridgeRoutes(
            bridgeRepository = dependencies.bridgeRepository,
        )
    }
}
