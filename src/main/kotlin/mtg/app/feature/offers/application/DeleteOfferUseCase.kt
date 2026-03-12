package mtg.app.feature.offers.application

import mtg.app.core.error.ValidationException
import mtg.app.feature.offers.domain.OfferRepository

class DeleteOfferUseCase(
    private val repository: OfferRepository,
) {
    suspend operator fun invoke(id: String, ownerUserId: String): Boolean {
        val normalizedId = id.trim()
        val normalizedOwnerUserId = ownerUserId.trim()
        if (normalizedId.isBlank()) throw ValidationException("id is required")
        if (normalizedOwnerUserId.isBlank()) throw ValidationException("ownerUserId is required")
        return repository.deleteOwned(normalizedId, normalizedOwnerUserId)
    }
}
