package mtg.app.feature.matches.application

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import mtg.app.feature.offers.domain.Offer
import mtg.app.feature.offers.domain.OfferRepository
import mtg.app.feature.offers.domain.OfferType
import mtg.app.feature.users.application.LoadNicknamesUseCase
import mtg.app.feature.users.domain.UserProfileRepository

class LoadMatchesUseCaseTest {
    @Test
    fun `groups matches by card and counterpart and uses nicknames`() = runTest {
        val repository = FakeOfferRepository(
            listOf(
                offer(id = "1", userId = "me", cardId = "c1", cardName = "Lightning Bolt", type = OfferType.BUY, price = 3.0),
                offer(id = "2", userId = "seller-a", cardId = "c1", cardName = "Lightning Bolt", type = OfferType.SELL, price = 2.5),
                offer(id = "3", userId = "seller-a", cardId = "c1", cardName = "Lightning Bolt", type = OfferType.SELL, price = 2.0),
                offer(id = "4", userId = "seller-b", cardId = "c1", cardName = "Lightning Bolt", type = OfferType.SELL, price = 2.7),
            )
        )
        val loadNicknames = LoadNicknamesUseCase(
            repository = FakeUserProfileRepository(
                mapOf("seller-a" to "Alice")
            )
        )
        val useCase = LoadMatchesUseCase(repository, loadNicknames)

        val result = useCase(userId = "me")

        assertEquals(2, result.size)
        assertEquals("Alice", result[0].counterpartDisplayName)
        assertEquals("seller-a", result[0].counterpartUserId)
        assertEquals(2, result[0].counterpartOfferCount)
        assertEquals(2.0, result[0].counterpartBestPrice)
        assertEquals("seller-b", result[1].counterpartUserId)
        assertEquals("seller-b", result[1].counterpartDisplayName)
    }

    @Test
    fun `returns empty list when user has no offers`() = runTest {
        val useCase = LoadMatchesUseCase(
            offerRepository = FakeOfferRepository(emptyList()),
            loadNicknames = LoadNicknamesUseCase(FakeUserProfileRepository(emptyMap())),
        )

        val result = useCase(userId = "me")

        assertEquals(emptyList(), result)
    }

    private fun offer(
        id: String,
        userId: String,
        cardId: String,
        cardName: String,
        type: OfferType,
        price: Double?,
    ) = Offer(
        id = id,
        userId = userId,
        cardId = cardId,
        cardName = cardName,
        cardTypeLine = null,
        cardImageUrl = null,
        type = type,
        price = price,
        createdAt = 1L,
    )

    private class FakeOfferRepository(
        private val offers: List<Offer>,
    ) : OfferRepository {
        override suspend fun create(offer: Offer): Offer = error("not needed")

        override suspend fun list(cardId: String?, userId: String?, type: OfferType?): List<Offer> {
            return offers.filter { offer ->
                (cardId == null || offer.cardId == cardId) &&
                    (userId == null || offer.userId == userId) &&
                    (type == null || offer.type == type)
            }
        }

        override suspend fun deleteOwned(id: String, ownerUserId: String): Boolean = error("not needed")
    }

    private class FakeUserProfileRepository(
        private val nicknames: Map<String, String>,
    ) : UserProfileRepository {
        override suspend fun upsertNickname(userId: String, nickname: String) = Unit

        override suspend fun loadNickname(userId: String): String? = nicknames[userId]

        override suspend fun loadNicknames(userIds: Set<String>): Map<String, String> {
            return nicknames.filterKeys { it in userIds }
        }
    }
}
