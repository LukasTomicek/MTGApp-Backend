package mtg.app.feature.wallet.infrastructure

import mtg.app.feature.wallet.domain.StorePurchaseVerifier
import mtg.app.feature.wallet.domain.VerifiedWalletPurchase
import mtg.app.feature.wallet.domain.WalletPlatform

class DefaultStorePurchaseVerifier(
    private val googlePlayPurchaseVerifier: GooglePlayPurchaseVerifier,
    private val appleAppStorePurchaseVerifier: AppleAppStorePurchaseVerifier,
) : StorePurchaseVerifier {
    override suspend fun verify(
        platform: WalletPlatform,
        productId: String,
        storeTransactionId: String,
        purchaseToken: String?,
    ): VerifiedWalletPurchase {
        return when (platform) {
            WalletPlatform.ANDROID -> googlePlayPurchaseVerifier.verify(
                productId = productId,
                purchaseToken = purchaseToken.orEmpty().ifBlank {
                    error("Google Play purchaseToken is required")
                },
            )

            WalletPlatform.IOS -> appleAppStorePurchaseVerifier.verify(
                productId = productId,
                transactionId = storeTransactionId,
            )
        }
    }
}
