package mtg.app.feature.offers.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import mtg.app.core.error.ValidationException
import mtg.app.feature.offers.application.CreateOfferUseCase
import mtg.app.feature.offers.application.DeleteOfferUseCase
import mtg.app.feature.offers.application.ListOffersUseCase
import mtg.app.feature.offers.domain.Offer
import mtg.app.feature.offers.domain.OfferType

@Serializable
data class CreateOfferRequest(
    val userId: String,
    val cardId: String,
    val cardName: String,
    val cardTypeLine: String? = null,
    val cardImageUrl: String? = null,
    val type: String = OfferType.SELL.name,
    val price: Double? = null,
)

@Serializable
private data class OfferResponse(
    val id: String,
    val userId: String,
    val cardId: String,
    val cardName: String,
    val cardTypeLine: String?,
    val cardImageUrl: String?,
    val type: String,
    val price: Double?,
    val createdAt: Long,
)

@Serializable
private data class DeleteOfferResponse(
    val deleted: Boolean,
)

fun Route.registerOfferRoutes(
    createOffer: CreateOfferUseCase,
    listOffers: ListOffersUseCase,
    deleteOffer: DeleteOfferUseCase,
) {
    route("/v1/offers") {
        get {
            val cardId = call.request.queryParameters["cardId"]
            val userId = call.request.queryParameters["userId"]
            val type = call.request.queryParameters["type"]?.toOfferTypeOrNull()
            call.respond(
                listOffers(cardId = cardId, userId = userId, type = type)
                    .map { it.toResponse() }
            )
        }

        post {
            val request = call.receive<CreateOfferRequest>()
            val created = createOffer(
                userId = request.userId,
                cardId = request.cardId,
                cardName = request.cardName,
                cardTypeLine = request.cardTypeLine,
                cardImageUrl = request.cardImageUrl,
                type = request.type.toOfferTypeStrict(),
                price = request.price,
            )
            call.respond(HttpStatusCode.Created, created.toResponse())
        }

        delete("/{id}") {
            val id = call.parameters["id"].orEmpty()
            val deleted = deleteOffer(id)
            if (deleted) {
                call.respond(HttpStatusCode.OK, DeleteOfferResponse(deleted = true))
            } else {
                call.respond(HttpStatusCode.NotFound, DeleteOfferResponse(deleted = false))
            }
        }
    }
}

private fun Offer.toResponse(): OfferResponse {
    return OfferResponse(
        id = id,
        userId = userId,
        cardId = cardId,
        cardName = cardName,
        cardTypeLine = cardTypeLine,
        cardImageUrl = cardImageUrl,
        type = type.name,
        price = price,
        createdAt = createdAt,
    )
}

private fun String.toOfferTypeStrict(): OfferType {
    val normalized = trim().uppercase()
    return runCatching { OfferType.valueOf(normalized) }
        .getOrElse { throw ValidationException("type must be BUY or SELL") }
}

private fun String.toOfferTypeOrNull(): OfferType? {
    val normalized = trim()
    if (normalized.isBlank()) return null
    return runCatching { OfferType.valueOf(normalized.uppercase()) }
        .getOrElse { throw ValidationException("type must be BUY or SELL") }
}
