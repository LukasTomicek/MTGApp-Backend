package mtg.app.feature.offers.application

import mtg.app.core.error.ValidationException
import mtg.app.feature.offers.domain.OfferRepository

class DeleteOfferUseCase(
    private val repository: OfferRepository,
) {
    suspend operator fun invoke(id: String): Boolean {
        val normalizedId = id.trim()
        if (normalizedId.isBlank()) throw ValidationException("id is required")
        return repository.delete(normalizedId)
    }
}
