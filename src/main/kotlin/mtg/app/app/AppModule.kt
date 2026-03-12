package mtg.app.app

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.routing.routing
import mtg.app.core.plugins.configureMonitoring
import mtg.app.core.plugins.configureSerialization
import mtg.app.core.plugins.configureStatusPages
import mtg.app.feature.chat.api.registerChatRoutes
import mtg.app.feature.health.api.registerHealthRoutes
import mtg.app.feature.market.api.registerMarketRoutes
import mtg.app.feature.matches.api.registerMatchRoutes
import mtg.app.feature.offers.api.registerOfferRoutes
import mtg.app.feature.users.api.registerUserCollectionRoutes
import mtg.app.feature.users.api.registerUserMapPinRoutes
import mtg.app.feature.users.api.registerUserMarketRoutes
import mtg.app.feature.users.api.registerUserNotificationRoutes
import mtg.app.feature.users.api.registerUserProfileRoutes
import mtg.app.feature.users.api.registerUserStateRoutes

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
            authVerifier = dependencies.firebaseAuthVerifier,
            createOffer = dependencies.createOffer,
            listOffers = dependencies.listOffers,
            deleteOffer = dependencies.deleteOffer,
            syncOffers = dependencies.syncOffers,
        )

        registerMarketRoutes(
            authVerifier = dependencies.firebaseAuthVerifier,
            loadMarketCards = dependencies.loadMarketCards,
            loadMarketSellers = dependencies.loadMarketSellers,
        )

        registerMatchRoutes(
            authVerifier = dependencies.firebaseAuthVerifier,
            loadMatches = dependencies.loadMatches,
            syncMatchNotifications = dependencies.syncMatchNotifications,
        )

        registerUserProfileRoutes(
            authVerifier = dependencies.firebaseAuthVerifier,
            saveUserNickname = dependencies.saveUserNickname,
            loadUserNickname = dependencies.loadUserNickname,
        )

        registerUserStateRoutes(
            authVerifier = dependencies.firebaseAuthVerifier,
            bridgeRepository = dependencies.bridgeRepository,
        )

        registerUserCollectionRoutes(
            authVerifier = dependencies.firebaseAuthVerifier,
            bridgeRepository = dependencies.bridgeRepository,
        )

        registerUserMapPinRoutes(
            authVerifier = dependencies.firebaseAuthVerifier,
            bridgeRepository = dependencies.bridgeRepository,
        )

        registerUserNotificationRoutes(
            authVerifier = dependencies.firebaseAuthVerifier,
            bridgeRepository = dependencies.bridgeRepository,
        )

        registerUserMarketRoutes(
            authVerifier = dependencies.firebaseAuthVerifier,
            bridgeRepository = dependencies.bridgeRepository,
        )

        registerChatRoutes(
            authVerifier = dependencies.firebaseAuthVerifier,
            bridgeRepository = dependencies.bridgeRepository,
        )

    }
}
