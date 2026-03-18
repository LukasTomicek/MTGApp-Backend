package mtg.app.feature.payments.application

import mtg.app.feature.payments.domain.PaymentsRepository
import mtg.app.feature.payments.domain.TradeOrder

class ListBoughtOrdersUseCase(
    private val repository: PaymentsRepository,
) {
    suspend operator fun invoke(userId: String): List<TradeOrder> {
        return repository.listOrdersBoughtByUser(userId)
    }
}
