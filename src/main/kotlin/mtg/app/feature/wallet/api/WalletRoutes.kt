package mtg.app.feature.wallet.api

import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import mtg.app.core.auth.FirebaseAuthVerifier
import mtg.app.core.auth.requireFirebasePrincipal
import mtg.app.core.error.ValidationException
import mtg.app.feature.wallet.application.ConfirmWalletPurchaseUseCase
import mtg.app.feature.wallet.application.LoadWalletBalanceUseCase
import mtg.app.feature.wallet.domain.WalletPlatform

@Serializable
private data class WalletBalanceResponse(
    val credits: Int,
)

@Serializable
data class ConfirmWalletPurchaseRequest(
    val platform: String,
    val productId: String,
    val storeTransactionId: String,
    val purchaseToken: String? = null,
)

fun Route.registerWalletRoutes(
    authVerifier: FirebaseAuthVerifier,
    loadWalletBalance: LoadWalletBalanceUseCase,
    confirmWalletPurchase: ConfirmWalletPurchaseUseCase,
) {
    route("/v1/users/me/wallet") {
        get {
            val principal = call.requireFirebasePrincipal(authVerifier)
            val credits = loadWalletBalance(userId = principal.uid)
            call.respond(WalletBalanceResponse(credits = credits))
        }

        post("/purchases/confirm") {
            val principal = call.requireFirebasePrincipal(authVerifier)
            val request = call.receive<ConfirmWalletPurchaseRequest>()
            val platform = request.platform.toWalletPlatform()
            val credits = confirmWalletPurchase(
                userId = principal.uid,
                platform = platform,
                productId = request.productId,
                storeTransactionId = request.storeTransactionId,
                purchaseToken = request.purchaseToken,
            )
            call.respond(WalletBalanceResponse(credits = credits))
        }
    }
}

private fun String.toWalletPlatform(): WalletPlatform {
    return when (trim().lowercase()) {
        "android" -> WalletPlatform.ANDROID
        "ios" -> WalletPlatform.IOS
        else -> throw ValidationException("Unsupported wallet platform")
    }
}
