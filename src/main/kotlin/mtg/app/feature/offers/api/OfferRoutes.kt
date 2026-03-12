package mtg.app.feature.offers.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import mtg.app.core.auth.FirebaseAuthVerifier
import mtg.app.core.auth.requireFirebasePrincipal
import mtg.app.core.error.ValidationException
import mtg.app.feature.offers.application.CreateOfferUseCase
import mtg.app.feature.offers.application.DeleteOfferUseCase
import mtg.app.feature.offers.application.ListOffersUseCase
import mtg.app.feature.offers.application.SyncOffersUseCase
import mtg.app.feature.offers.domain.Offer
import mtg.app.feature.offers.domain.OfferType

@Serializable
data class CreateOfferRequest(
    val cardId: String,
    val cardName: String,
    val cardTypeLine: String? = null,
    val cardImageUrl: String? = null,
    val type: String = OfferType.SELL.name,
    val price: Double? = null,
)

@Serializable
private data class SyncOffersRequest(
    val type: String,
    val entries: List<SyncOfferEntry>,
)

@Serializable
private data class SyncOfferEntry(
    val cardId: String,
    val cardName: String,
    val cardTypeLine: String? = null,
    val cardImageUrl: String? = null,
    val price: Double? = null,
)

@Serializable
private data class SyncOffersResponse(
    val deletedCount: Int,
    val createdCount: Int,
    val unchangedCount: Int,
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
    authVerifier: FirebaseAuthVerifier,
    createOffer: CreateOfferUseCase,
    listOffers: ListOffersUseCase,
    deleteOffer: DeleteOfferUseCase,
    syncOffers: SyncOffersUseCase,
) {
    route("/v1/offers") {
        get {
            val cardId = call.request.queryParameters["cardId"]
            val userId = call.request.queryParameters["userId"]
            val type = call.request.queryParameters["type"]?.toOfferTypeOrNull()
            val effectiveUserId = if (userId.isNullOrBlank()) {
                null
            } else {
                val principal = call.requireFirebasePrincipal(authVerifier)
                authVerifier.requireSameUser(userId, principal)
            }
            call.respond(
                listOffers(cardId = cardId, userId = effectiveUserId, type = type)
                    .map { it.toResponse() }
            )
        }

        get("/me") {
            val principal = call.requireFirebasePrincipal(authVerifier)
            val cardId = call.request.queryParameters["cardId"]
            val type = call.request.queryParameters["type"]?.toOfferTypeOrNull()
            call.respond(
                listOffers(cardId = cardId, userId = principal.uid, type = type)
                    .map { it.toResponse() }
            )
        }

        post {
            val principal = call.requireFirebasePrincipal(authVerifier)
            val request = call.receive<CreateOfferRequest>()
            val created = createOffer(
                userId = principal.uid,
                cardId = request.cardId,
                cardName = request.cardName,
                cardTypeLine = request.cardTypeLine,
                cardImageUrl = request.cardImageUrl,
                type = request.type.toOfferTypeStrict(),
                price = request.price,
            )
            call.respond(HttpStatusCode.Created, created.toResponse())
        }

        put("/me") {
            val principal = call.requireFirebasePrincipal(authVerifier)
            val request = call.receive<SyncOffersRequest>()
            val result = syncOffers(
                ownerUserId = principal.uid,
                type = request.type.toOfferTypeStrict(),
                desiredEntries = request.entries.map {
                    SyncOffersUseCase.DesiredOfferEntry(
                        cardId = it.cardId,
                        cardName = it.cardName,
                        cardTypeLine = it.cardTypeLine,
                        cardImageUrl = it.cardImageUrl,
                        price = it.price,
                    )
                }
            )
            call.respond(
                HttpStatusCode.OK,
                SyncOffersResponse(
                    deletedCount = result.deletedCount,
                    createdCount = result.createdCount,
                    unchangedCount = result.unchangedCount,
                )
            )
        }

        delete("/{id}") {
            val principal = call.requireFirebasePrincipal(authVerifier)
            val id = call.parameters["id"].orEmpty()
            val deleted = deleteOffer(id = id, ownerUserId = principal.uid)
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
