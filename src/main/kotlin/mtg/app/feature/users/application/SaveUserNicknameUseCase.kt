package mtg.app.feature.users.application

import mtg.app.core.error.ValidationException
import mtg.app.feature.users.domain.UserProfileRepository

class SaveUserNicknameUseCase(
    private val repository: UserProfileRepository,
) {
    suspend operator fun invoke(userId: String, nickname: String) {
        val normalizedUserId = userId.trim()
        val normalizedNickname = nickname.trim()

        if (normalizedUserId.isBlank()) throw ValidationException("userId is required")
        if (normalizedNickname.isBlank()) throw ValidationException("nickname is required")

        repository.upsertNickname(
            userId = normalizedUserId,
            nickname = normalizedNickname,
        )
    }
}
