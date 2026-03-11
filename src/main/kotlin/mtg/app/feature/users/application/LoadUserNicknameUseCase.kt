package mtg.app.feature.users.application

import mtg.app.core.error.ValidationException
import mtg.app.feature.users.domain.UserProfileRepository

class LoadUserNicknameUseCase(
    private val repository: UserProfileRepository,
) {
    suspend operator fun invoke(userId: String): String? {
        val normalizedUserId = userId.trim()
        if (normalizedUserId.isBlank()) throw ValidationException("userId is required")
        return repository.loadNickname(userId = normalizedUserId)
    }
}
