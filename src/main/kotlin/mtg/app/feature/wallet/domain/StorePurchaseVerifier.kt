package mtg.app.feature.wallet.domain

interface StorePurchaseVerifier {
    suspend fun verify(
        platform: WalletPlatform,
        productId: String,
        storeTransactionId: String,
        purchaseToken: String?,
    ): VerifiedWalletPurchase
}
