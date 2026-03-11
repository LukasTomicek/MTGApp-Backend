package mtg.app.feature.users.application

import mtg.app.feature.users.domain.UserProfileRepository

class LoadNicknamesUseCase(
    private val repository: UserProfileRepository,
) {
    suspend operator fun invoke(userIds: Set<String>): Map<String, String> {
        val normalized = userIds
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
        if (normalized.isEmpty()) return emptyMap()
        return repository.loadNicknames(normalized)
    }
}
