package mtg.app.feature.users.application

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import mtg.app.core.error.ValidationException
import mtg.app.feature.users.domain.UserProfileRepository

class UserNicknameUseCasesTest {
    @Test
    fun `save nickname trims values before persisting`() = kotlinx.coroutines.test.runTest {
        val repository = FakeUserProfileRepository()
        val useCase = SaveUserNicknameUseCase(repository)

        useCase(userId = " u1 ", nickname = " Lukas ")

        assertEquals("u1", repository.savedUserId)
        assertEquals("Lukas", repository.savedNickname)
    }

    @Test
    fun `save nickname rejects blank values`() = kotlinx.coroutines.test.runTest {
        val useCase = SaveUserNicknameUseCase(FakeUserProfileRepository())

        assertFailsWith<ValidationException> {
            useCase(userId = " ", nickname = "Lukas")
        }
        assertFailsWith<ValidationException> {
            useCase(userId = "u1", nickname = " ")
        }
    }

    @Test
    fun `load nickname trims user id and returns repository value`() = kotlinx.coroutines.test.runTest {
        val repository = FakeUserProfileRepository(
            nicknames = mutableMapOf("u1" to "Lukas")
        )
        val useCase = LoadUserNicknameUseCase(repository)

        val nickname = useCase(userId = " u1 ")

        assertEquals("u1", repository.loadedUserId)
        assertEquals("Lukas", nickname)
    }

    @Test
    fun `load nickname rejects blank user id`() = kotlinx.coroutines.test.runTest {
        val useCase = LoadUserNicknameUseCase(FakeUserProfileRepository())

        assertFailsWith<ValidationException> {
            useCase(userId = " ")
        }
    }

    private class FakeUserProfileRepository(
        val nicknames: MutableMap<String, String> = mutableMapOf(),
    ) : UserProfileRepository {
        var savedUserId: String? = null
        var savedNickname: String? = null
        var loadedUserId: String? = null

        override suspend fun upsertNickname(userId: String, nickname: String) {
            savedUserId = userId
            savedNickname = nickname
            nicknames[userId] = nickname
        }

        override suspend fun loadNickname(userId: String): String? {
            loadedUserId = userId
            return nicknames[userId]
        }

        override suspend fun loadNicknames(userIds: Set<String>): Map<String, String> {
            return nicknames.filterKeys { it in userIds }
        }
    }
}
