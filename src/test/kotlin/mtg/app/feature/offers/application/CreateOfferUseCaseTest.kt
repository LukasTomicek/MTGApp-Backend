package mtg.app.feature.offers.application

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import mtg.app.core.error.ValidationException
import mtg.app.feature.offers.domain.Offer
import mtg.app.feature.offers.domain.OfferRepository
import mtg.app.feature.offers.domain.OfferType

class CreateOfferUseCaseTest {
    @Test
    fun `normalizes values before persisting`() = kotlinx.coroutines.test.runTest {
        val repository = FakeOfferRepository()
        val useCase = CreateOfferUseCase(repository)

        val created = useCase(
            userId = " u1 ",
            cardId = " c1 ",
            cardName = " Lightning Bolt ",
            cardTypeLine = " Instant ",
            cardImageUrl = " https://img ",
            type = OfferType.SELL,
            price = 2.5,
        )

        assertEquals(created, repository.created.single())
        assertEquals("u1", created.userId)
        assertEquals("c1", created.cardId)
        assertEquals("Lightning Bolt", created.cardName)
        assertEquals("Instant", created.cardTypeLine)
        assertEquals("https://img", created.cardImageUrl)
        assertEquals(OfferType.SELL, created.type)
        assertEquals(2.5, created.price)
        assertNotNull(created.id)
        assert(created.id.startsWith("offer_"))
        assert(created.createdAt > 0)
    }

    @Test
    fun `drops blank optional fields`() = kotlinx.coroutines.test.runTest {
        val repository = FakeOfferRepository()
        val useCase = CreateOfferUseCase(repository)

        val created = useCase(
            userId = "u1",
            cardId = "c1",
            cardName = "Lightning Bolt",
            cardTypeLine = "   ",
            cardImageUrl = "   ",
            type = OfferType.BUY,
            price = null,
        )

        assertEquals(null, created.cardTypeLine)
        assertEquals(null, created.cardImageUrl)
        assertEquals(null, created.price)
    }

    @Test
    fun `rejects blank required fields and negative price`() = kotlinx.coroutines.test.runTest {
        val useCase = CreateOfferUseCase(FakeOfferRepository())

        assertFailsWith<ValidationException> {
            useCase(" ", "c1", "Bolt", null, null, OfferType.SELL, 1.0)
        }
        assertFailsWith<ValidationException> {
            useCase("u1", " ", "Bolt", null, null, OfferType.SELL, 1.0)
        }
        assertFailsWith<ValidationException> {
            useCase("u1", "c1", " ", null, null, OfferType.SELL, 1.0)
        }
        assertFailsWith<ValidationException> {
            useCase("u1", "c1", "Bolt", null, null, OfferType.SELL, -0.1)
        }
    }

    private class FakeOfferRepository : OfferRepository {
        val created = mutableListOf<Offer>()

        override suspend fun create(offer: Offer): Offer {
            created += offer
            return offer
        }

        override suspend fun list(cardId: String?, userId: String?, type: OfferType?): List<Offer> = emptyList()

        override suspend fun deleteOwned(id: String, ownerUserId: String): Boolean = false
    }
}
