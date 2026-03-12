package mtg.app.feature.offers.application

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import mtg.app.core.error.ValidationException
import mtg.app.feature.offers.domain.Offer
import mtg.app.feature.offers.domain.OfferRepository
import mtg.app.feature.offers.domain.OfferType

class SyncOffersUseCaseTest {
    @Test
    fun `keeps unchanged offers and only creates missing ones`() = kotlinx.coroutines.test.runTest {
        val repository = FakeOfferRepository(
            offers = mutableListOf(
                offer(id = "existing-1", userId = "u1", cardId = "c1", cardName = "Bolt", type = OfferType.SELL, price = 2.5),
            )
        )
        val useCase = SyncOffersUseCase(repository)

        val result = useCase(
            ownerUserId = "u1",
            type = OfferType.SELL,
            desiredEntries = listOf(
                SyncOffersUseCase.DesiredOfferEntry(cardId = "c1", cardName = "Bolt", price = 2.5),
                SyncOffersUseCase.DesiredOfferEntry(cardId = "c2", cardName = "Counterspell", price = 3.0),
            )
        )

        assertEquals(0, result.deletedCount)
        assertEquals(1, result.createdCount)
        assertEquals(1, result.unchangedCount)
        assertEquals(listOf("existing-1"), repository.offers.filter { it.cardId == "c1" }.map { it.id })
        assertEquals(setOf("c1", "c2"), repository.offers.map { it.cardId }.toSet())
    }

    @Test
    fun `deletes obsolete offers`() = kotlinx.coroutines.test.runTest {
        val repository = FakeOfferRepository(
            offers = mutableListOf(
                offer(id = "existing-1", userId = "u1", cardId = "c1", cardName = "Bolt", type = OfferType.BUY, price = 2.0),
                offer(id = "existing-2", userId = "u1", cardId = "c2", cardName = "Drain", type = OfferType.BUY, price = 5.0),
            )
        )
        val useCase = SyncOffersUseCase(repository)

        val result = useCase(
            ownerUserId = "u1",
            type = OfferType.BUY,
            desiredEntries = listOf(
                SyncOffersUseCase.DesiredOfferEntry(cardId = "c2", cardName = "Drain", price = 5.0),
            )
        )

        assertEquals(1, result.deletedCount)
        assertEquals(0, result.createdCount)
        assertEquals(1, result.unchangedCount)
        assertEquals(listOf("c2"), repository.offers.map { it.cardId })
    }

    @Test
    fun `rejects negative price`() = kotlinx.coroutines.test.runTest {
        val useCase = SyncOffersUseCase(FakeOfferRepository())

        assertFailsWith<ValidationException> {
            useCase(
                ownerUserId = "u1",
                type = OfferType.SELL,
                desiredEntries = listOf(
                    SyncOffersUseCase.DesiredOfferEntry(cardId = "c1", cardName = "Bolt", price = -1.0),
                )
            )
        }
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
        val offers: MutableList<Offer> = mutableListOf(),
    ) : OfferRepository {
        override suspend fun create(offer: Offer): Offer {
            offers += offer
            return offer
        }

        override suspend fun list(cardId: String?, userId: String?, type: OfferType?): List<Offer> {
            return offers.filter { offer ->
                (cardId == null || offer.cardId == cardId) &&
                    (userId == null || offer.userId == userId) &&
                    (type == null || offer.type == type)
            }
        }

        override suspend fun deleteOwned(id: String, ownerUserId: String): Boolean {
            return offers.removeIf { it.id == id && it.userId == ownerUserId }
        }
    }
}
