package mtg.app.app

import io.ktor.server.config.ApplicationConfig
import mtg.app.core.auth.FirebaseAuthVerifier
import mtg.app.core.db.DatabaseFactory
import mtg.app.feature.bridge.infrastructure.PostgresBridgeRepository
import mtg.app.feature.health.application.GetHealthStatusUseCase
import mtg.app.feature.market.application.LoadMarketCardsUseCase
import mtg.app.feature.market.application.LoadMarketSellersUseCase
import mtg.app.feature.market.application.MarketVisibilitySupport
import mtg.app.feature.matches.application.LoadMatchesUseCase
import mtg.app.feature.matches.application.SyncMatchNotificationsUseCase
import mtg.app.feature.offers.application.CreateOfferUseCase
import mtg.app.feature.offers.application.DeleteOfferUseCase
import mtg.app.feature.offers.application.ListOffersUseCase
import mtg.app.feature.offers.application.SyncOffersUseCase
import mtg.app.feature.offers.domain.OfferRepository
import mtg.app.feature.offers.infrastructure.PostgresOfferRepository
import mtg.app.feature.users.application.LoadNicknamesUseCase
import mtg.app.feature.users.application.LoadUserNicknameUseCase
import mtg.app.feature.users.application.SaveUserNicknameUseCase
import mtg.app.feature.users.domain.UserProfileRepository
import mtg.app.feature.users.infrastructure.PostgresUserProfileRepository

class AppDependencies(
    config: ApplicationConfig,
) : AutoCloseable {
    private val databaseFactory = DatabaseFactory(config = config)
    private val offerRepository: OfferRepository = PostgresOfferRepository(
        dataSource = databaseFactory.dataSource(),
    )
    private val userProfileRepository: UserProfileRepository = PostgresUserProfileRepository(
        dataSource = databaseFactory.dataSource(),
    )
    val bridgeRepository = PostgresBridgeRepository(
        dataSource = databaseFactory.dataSource(),
    )
    private val marketVisibilitySupport = MarketVisibilitySupport(
        bridgeRepository = bridgeRepository,
    )

    val firebaseAuthVerifier = FirebaseAuthVerifier(
        projectId = config.propertyOrNull("firebase.projectId")?.getString()?.trim().orEmpty().ifBlank { "mtglocaltrade" }
    )

    val getHealthStatus = GetHealthStatusUseCase()

    val createOffer = CreateOfferUseCase(repository = offerRepository)
    val listOffers = ListOffersUseCase(repository = offerRepository)
    val deleteOffer = DeleteOfferUseCase(repository = offerRepository)
    val syncOffers = SyncOffersUseCase(repository = offerRepository)

    val loadNicknames = LoadNicknamesUseCase(repository = userProfileRepository)
    val loadMarketCards = LoadMarketCardsUseCase(
        offerRepository = offerRepository,
        visibilitySupport = marketVisibilitySupport,
    )
    val loadMarketSellers = LoadMarketSellersUseCase(
        offerRepository = offerRepository,
        loadNicknames = loadNicknames,
        visibilitySupport = marketVisibilitySupport,
    )
    val loadMatches = LoadMatchesUseCase(
        offerRepository = offerRepository,
        loadNicknames = loadNicknames,
    )
    val syncMatchNotifications = SyncMatchNotificationsUseCase(
        offerRepository = offerRepository,
        bridgeRepository = bridgeRepository,
        loadNicknames = loadNicknames,
    )

    val saveUserNickname = SaveUserNicknameUseCase(repository = userProfileRepository)
    val loadUserNickname = LoadUserNicknameUseCase(repository = userProfileRepository)

    override fun close() {
        databaseFactory.close()
    }
}
