package mtg.app.feature.users.domain

interface UserProfileRepository {
    suspend fun upsertNickname(userId: String, nickname: String)
    suspend fun loadNickname(userId: String): String?
    suspend fun loadNicknames(userIds: Set<String>): Map<String, String>
}
