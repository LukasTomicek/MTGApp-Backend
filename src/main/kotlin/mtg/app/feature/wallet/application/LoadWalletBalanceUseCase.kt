package mtg.app.feature.wallet.application

import mtg.app.core.error.ValidationException
import mtg.app.feature.wallet.domain.WalletRepository

class LoadWalletBalanceUseCase(
    private val repository: WalletRepository,
) {
    suspend operator fun invoke(userId: String): Int {
        val normalizedUserId = userId.trim()
        if (normalizedUserId.isBlank()) throw ValidationException("userId is required")
        return repository.loadBalance(normalizedUserId)
    }
}
