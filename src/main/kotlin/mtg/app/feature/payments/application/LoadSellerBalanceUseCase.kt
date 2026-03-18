package mtg.app.feature.payments.application

import mtg.app.feature.payments.domain.PaymentsRepository

class LoadSellerBalanceUseCase(
    private val repository: PaymentsRepository,
) {
    suspend operator fun invoke(userId: String): Long {
        return repository.calculateSellerBalanceMinor(userId = userId)
    }
}
