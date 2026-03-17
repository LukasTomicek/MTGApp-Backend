package mtg.app.feature.wallet.domain

interface WalletRepository {
    suspend fun loadBalance(userId: String): Int
    suspend fun confirmPurchase(userId: String, purchase: VerifiedWalletPurchase): Int
}

enum class WalletPlatform {
    ANDROID,
    IOS,
}

data class VerifiedWalletPurchase(
    val platform: WalletPlatform,
    val productId: String,
    val storeTransactionId: String,
    val purchaseToken: String? = null,
)
