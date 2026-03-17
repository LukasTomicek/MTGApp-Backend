package mtg.app.app

import io.ktor.server.config.ApplicationConfig
import mtg.app.core.auth.FirebaseAuthVerifier
import mtg.app.core.db.DatabaseFactory
import mtg.app.feature.bridge.infrastructure.PostgresBridgeRepository
import mtg.app.feature.bridge.infrastructure.PostgresChatStore
import mtg.app.feature.bridge.infrastructure.PostgresDocumentStoreSupport
import mtg.app.feature.bridge.infrastructure.PostgresNotificationStore
import mtg.app.feature.bridge.infrastructure.PostgresRatingStore
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
import mtg.app.feature.payments.application.CreateOrderCheckoutSessionUseCase
import mtg.app.feature.payments.application.CreateSellerOnboardingLinkUseCase
import mtg.app.feature.payments.application.EnsureTradeOrderUseCase
import mtg.app.feature.payments.application.GetSellerPayoutStatusUseCase
import mtg.app.feature.payments.application.GetTradeOrderUseCase
import mtg.app.feature.payments.application.HandleStripeWebhookUseCase
import mtg.app.feature.payments.application.ReleaseTradeOrderPayoutUseCase
import mtg.app.feature.payments.domain.PaymentsRepository
import mtg.app.feature.payments.domain.StripeGateway
import mtg.app.feature.payments.infrastructure.PostgresPaymentsRepository
import mtg.app.feature.payments.infrastructure.StripeHttpGateway
import mtg.app.feature.users.application.LoadNicknamesUseCase
import mtg.app.feature.users.application.LoadUserNicknameUseCase
import mtg.app.feature.users.application.SaveUserNicknameUseCase
import mtg.app.feature.users.domain.UserProfileRepository
import mtg.app.feature.users.infrastructure.PostgresUserProfileRepository

class AppDependencies(
    config: ApplicationConfig,
) : AutoCloseable {
    private val databaseFactory = DatabaseFactory(config = config)
    private val dataSource = databaseFactory.dataSource()
    private val documentSupport = PostgresDocumentStoreSupport(dataSource = dataSource)

    val offerRepository: OfferRepository = PostgresOfferRepository(
        dataSource = dataSource,
    )
    private val userProfileRepository: UserProfileRepository = PostgresUserProfileRepository(
        dataSource = dataSource,
    )
    private val paymentsRepository: PaymentsRepository = PostgresPaymentsRepository(
        dataSource = dataSource,
    )
    private val stripeGateway: StripeGateway = StripeHttpGateway(
        secretKey = config.propertyOrNull("payments.stripe.secretKey")?.getString().orEmpty(),
        webhookSecret = config.propertyOrNull("payments.stripe.webhookSecret")?.getString().orEmpty(),
        connectRefreshUrl = config.propertyOrNull("payments.stripe.connectRefreshUrl")?.getString().orEmpty(),
        connectReturnUrl = config.propertyOrNull("payments.stripe.connectReturnUrl")?.getString().orEmpty(),
        checkoutSuccessUrl = config.propertyOrNull("payments.stripe.checkoutSuccessUrl")?.getString().orEmpty(),
        checkoutCancelUrl = config.propertyOrNull("payments.stripe.checkoutCancelUrl")?.getString().orEmpty(),
        defaultCountry = config.propertyOrNull("payments.stripe.country")?.getString().orEmpty().ifBlank { "CZ" },
    )
    val bridgeRepository = PostgresBridgeRepository(
        dataSource = dataSource,
        support = documentSupport,
    )
    val notificationStore = PostgresNotificationStore(
        dataSource = dataSource,
        support = documentSupport,
    )
    val ratingStore = PostgresRatingStore(
        dataSource = dataSource,
        support = documentSupport,
    )
    val chatStore = PostgresChatStore(
        dataSource = dataSource,
        support = documentSupport,
        notificationStore = notificationStore,
    )
    private val marketVisibilitySupport = MarketVisibilitySupport(
        bridgeRepository = bridgeRepository,
        chatStore = chatStore,
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
        chatStore = chatStore,
        notificationStore = notificationStore,
        loadNicknames = loadNicknames,
    )

    val saveUserNickname = SaveUserNicknameUseCase(repository = userProfileRepository)
    val loadUserNickname = LoadUserNicknameUseCase(repository = userProfileRepository)

    val ensureTradeOrder = EnsureTradeOrderUseCase(
        repository = paymentsRepository,
        offerRepository = offerRepository,
        defaultCurrency = config.propertyOrNull("payments.defaultCurrency")?.getString().orEmpty().ifBlank { "czk" },
        feePercent = config.propertyOrNull("payments.platformFeePercent")?.getString()?.toDoubleOrNull() ?: 10.0,
    )
    val getTradeOrder = GetTradeOrderUseCase(repository = paymentsRepository)
    val getSellerPayoutStatus = GetSellerPayoutStatusUseCase(
        repository = paymentsRepository,
        stripeGateway = stripeGateway,
    )
    val createSellerOnboardingLink = CreateSellerOnboardingLinkUseCase(
        repository = paymentsRepository,
        stripeGateway = stripeGateway,
    )
    val createOrderCheckoutSession = CreateOrderCheckoutSessionUseCase(
        repository = paymentsRepository,
        stripeGateway = stripeGateway,
    )
    val releaseTradeOrderPayout = ReleaseTradeOrderPayoutUseCase(
        repository = paymentsRepository,
        stripeGateway = stripeGateway,
    )
    val handleStripeWebhook = HandleStripeWebhookUseCase(
        repository = paymentsRepository,
        stripeGateway = stripeGateway,
    )

    override fun close() {
        databaseFactory.close()
    }
}
