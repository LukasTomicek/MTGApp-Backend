package mtg.app.feature.offers.application

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import mtg.app.core.error.ValidationException
import mtg.app.feature.offers.domain.Offer
import mtg.app.feature.offers.domain.OfferRepository
import mtg.app.feature.offers.domain.OfferType

class DeleteOfferUseCaseTest {
    @Test
    fun `normalizes ids before deleting`() = kotlinx.coroutines.test.runTest {
        val repository = FakeOfferRepository(deleteResult = true)
        val useCase = DeleteOfferUseCase(repository)

        val deleted = useCase(id = " offer-1 ", ownerUserId = " u1 ")

        assertEquals(true, deleted)
        assertEquals("offer-1", repository.deletedId)
        assertEquals("u1", repository.deletedOwnerUserId)
    }

    @Test
    fun `returns repository delete result`() = kotlinx.coroutines.test.runTest {
        val useCase = DeleteOfferUseCase(FakeOfferRepository(deleteResult = false))

        val deleted = useCase(id = "offer-1", ownerUserId = "u1")

        assertEquals(false, deleted)
    }

    @Test
    fun `rejects blank id or owner`() = kotlinx.coroutines.test.runTest {
        val useCase = DeleteOfferUseCase(FakeOfferRepository(deleteResult = false))

        assertFailsWith<ValidationException> {
            useCase(id = " ", ownerUserId = "u1")
        }
        assertFailsWith<ValidationException> {
            useCase(id = "offer-1", ownerUserId = " ")
        }
    }

    private class FakeOfferRepository(
        private val deleteResult: Boolean,
    ) : OfferRepository {
        var deletedId: String? = null
        var deletedOwnerUserId: String? = null

        override suspend fun create(offer: Offer): Offer = offer

        override suspend fun list(cardId: String?, userId: String?, type: OfferType?): List<Offer> = emptyList()

        override suspend fun deleteOwned(id: String, ownerUserId: String): Boolean {
            deletedId = id
            deletedOwnerUserId = ownerUserId
            return deleteResult
        }
    }
}
