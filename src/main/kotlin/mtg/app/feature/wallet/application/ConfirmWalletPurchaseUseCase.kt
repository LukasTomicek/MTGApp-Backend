package mtg.app.feature.wallet.application

import mtg.app.core.error.ValidationException
import mtg.app.feature.wallet.domain.StorePurchaseVerifier
import mtg.app.feature.wallet.domain.WalletPlatform
import mtg.app.feature.wallet.domain.WalletRepository

class ConfirmWalletPurchaseUseCase(
    private val verifier: StorePurchaseVerifier,
    private val repository: WalletRepository,
) {
    suspend operator fun invoke(
        userId: String,
        platform: WalletPlatform,
        productId: String,
        storeTransactionId: String,
        purchaseToken: String?,
    ): Int {
        val normalizedUserId = userId.trim()
        val normalizedProductId = productId.trim()
        val normalizedTransactionId = storeTransactionId.trim()
        val normalizedToken = purchaseToken?.trim()?.takeUnless { it.isBlank() }

        if (normalizedUserId.isBlank()) throw ValidationException("userId is required")
        if (normalizedProductId.isBlank()) throw ValidationException("productId is required")
        if (normalizedTransactionId.isBlank()) throw ValidationException("storeTransactionId is required")

        val verifiedPurchase = verifier.verify(
            platform = platform,
            productId = normalizedProductId,
            storeTransactionId = normalizedTransactionId,
            purchaseToken = normalizedToken,
        )

        return repository.confirmPurchase(
            userId = normalizedUserId,
            purchase = verifiedPurchase,
        )
    }
}
