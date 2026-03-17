package mtg.app.feature.payments.application

import mtg.app.feature.payments.domain.PaymentsRepository
import mtg.app.feature.payments.domain.TradeOrder

class GetTradeOrderUseCase(
    private val repository: PaymentsRepository,
) {
    suspend operator fun invoke(chatId: String): TradeOrder? = repository.findOrderByChatId(chatId)
}
