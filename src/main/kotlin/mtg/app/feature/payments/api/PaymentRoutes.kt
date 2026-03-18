package mtg.app.feature.payments.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mtg.app.core.auth.FirebaseAuthVerifier
import mtg.app.core.auth.requireFirebasePrincipal
import mtg.app.core.error.ForbiddenException
import mtg.app.core.error.ValidationException
import mtg.app.feature.bridge.infrastructure.ChatRouteStore
import mtg.app.feature.payments.application.CreateOrderCheckoutSessionUseCase
import mtg.app.feature.payments.application.CreateSellerOnboardingLinkUseCase
import mtg.app.feature.payments.application.EnsureTradeOrderUseCase
import mtg.app.feature.payments.application.GetSellerPayoutStatusUseCase
import mtg.app.feature.payments.application.GetTradeOrderUseCase
import mtg.app.feature.payments.application.HandleStripeWebhookUseCase
import mtg.app.feature.payments.application.ListBoughtOrdersUseCase
import mtg.app.feature.payments.application.ListSoldOrdersUseCase
import mtg.app.feature.payments.application.RefundOrderUseCase
import mtg.app.feature.payments.domain.TradeOrder

@Serializable
data class SellerPayoutStatusResponse(
    val accountId: String? = null,
    val detailsSubmitted: Boolean = false,
    val chargesEnabled: Boolean = false,
    val payoutsEnabled: Boolean = false,
)

@Serializable
data class ExternalLinkResponse(
    val url: String,
)

@Serializable
data class TradeOrderResponse(
    val id: String,
    val chatId: String,
    val cardId: String,
    val cardName: String,
    val buyerUserId: String,
    val sellerUserId: String,
    val amountMinor: Long,
    val currency: String,
    val platformFeeMinor: Long,
    val sellerAmountMinor: Long,
    val paymentStatus: String,
    val payoutStatus: String,
    val paidAt: Long? = null,
    val paidOutAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

fun Route.registerPaymentRoutes(
    authVerifier: FirebaseAuthVerifier,
    chatStore: ChatRouteStore,
    getSellerPayoutStatus: GetSellerPayoutStatusUseCase,
    createSellerOnboardingLink: CreateSellerOnboardingLinkUseCase,
    ensureTradeOrder: EnsureTradeOrderUseCase,
    getTradeOrder: GetTradeOrderUseCase,
    listBoughtOrders: ListBoughtOrdersUseCase,
    listSoldOrders: ListSoldOrdersUseCase,
    refundOrder: RefundOrderUseCase,
    createOrderCheckoutSession: CreateOrderCheckoutSessionUseCase,
    handleStripeWebhook: HandleStripeWebhookUseCase,
) {
    route("/v1/payments/connect") {
        get("/status") {
            val principal = call.requireFirebasePrincipal(authVerifier)
            val status = getSellerPayoutStatus(userId = principal.uid)
            call.respond(
                SellerPayoutStatusResponse(
                    accountId = status.accountId,
                    detailsSubmitted = status.detailsSubmitted,
                    chargesEnabled = status.chargesEnabled,
                    payoutsEnabled = status.payoutsEnabled,
                )
            )
        }

        post("/onboarding-link") {
            val principal = call.requireFirebasePrincipal(authVerifier)
            val url = createSellerOnboardingLink(userId = principal.uid, email = principal.email)
            call.respond(ExternalLinkResponse(url = url))
        }
    }

    route("/v1/payments/orders") {
        get("/bought") {
            val principal = call.requireFirebasePrincipal(authVerifier)
            call.respond(listBoughtOrders(userId = principal.uid).map { it.toResponse() })
        }

        get("/sold") {
            val principal = call.requireFirebasePrincipal(authVerifier)
            call.respond(listSoldOrders(userId = principal.uid).map { it.toResponse() })
        }

        post("/{orderId}/refund") {
            val principal = call.requireFirebasePrincipal(authVerifier)
            val orderId = call.parameters["orderId"].orEmpty()
            if (orderId.isBlank()) throw ValidationException("orderId is required")
            val order = refundOrder(orderId = orderId, requesterUserId = principal.uid)
            call.respond(order.toResponse())
        }
    }

    route("/v1/chats/{chatId}/order") {
        get {
            val principal = call.requireFirebasePrincipal(authVerifier)
            val chatId = call.parameters["chatId"].orEmpty()
            val meta = requireChatMeta(chatStore, chatId)
            ensureParticipant(principal.uid, meta)
            val order = getTradeOrder(chatId)
            if (order == null) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.respond(order.toResponse())
            }
        }

        post {
            val principal = call.requireFirebasePrincipal(authVerifier)
            val chatId = call.parameters["chatId"].orEmpty()
            val meta = requireChatMeta(chatStore, chatId)
            ensureParticipant(principal.uid, meta)
            ensurePayableDeal(meta)
            val order = ensureTradeOrder(
                chatId = chatId,
                cardId = meta.stringOrThrow("cardId"),
                cardName = meta.stringOrThrow("cardName"),
                buyerUserId = meta.stringOrThrow("buyerUid"),
                sellerUserId = meta.stringOrThrow("sellerUid"),
            )
            call.respond(order.toResponse())
        }

        post("/checkout") {
            val principal = call.requireFirebasePrincipal(authVerifier)
            val chatId = call.parameters["chatId"].orEmpty()
            val meta = requireChatMeta(chatStore, chatId)
            ensureParticipant(principal.uid, meta)
            ensureBuyer(principal.uid, meta)
            ensurePayableDeal(meta)
            val order = ensureTradeOrder(
                chatId = chatId,
                cardId = meta.stringOrThrow("cardId"),
                cardName = meta.stringOrThrow("cardName"),
                buyerUserId = meta.stringOrThrow("buyerUid"),
                sellerUserId = meta.stringOrThrow("sellerUid"),
            )
            val checkout = createOrderCheckoutSession(orderId = order.chatId, buyerUserId = principal.uid)
            call.respond(ExternalLinkResponse(url = checkout.url))
        }
    }

    post("/v1/webhooks/stripe") {
        val payload = call.receiveText()
        handleStripeWebhook(payload = payload, signatureHeader = call.request.headers["Stripe-Signature"])
        call.respond(HttpStatusCode.OK)
    }
}

private fun requireChatMeta(chatStore: ChatRouteStore, chatId: String): JsonObject {
    if (chatId.isBlank()) throw ValidationException("chatId is required")
    return chatStore.getChatMeta(chatId) ?: throw ValidationException("Chat not found")
}

private fun ensureParticipant(uid: String, meta: JsonObject) {
    val buyerUid = meta.stringOrThrow("buyerUid")
    val sellerUid = meta.stringOrThrow("sellerUid")
    if (uid != buyerUid && uid != sellerUid) throw ForbiddenException("You are not a participant of this chat")
}

private fun ensureBuyer(uid: String, meta: JsonObject) {
    val buyerUid = meta.stringOrThrow("buyerUid")
    if (uid != buyerUid) throw ForbiddenException("Only buyer can start checkout")
}

private fun ensurePayableDeal(meta: JsonObject) {
    val dealStatus = meta.stringOrEmpty("dealStatus")
    if (dealStatus.equals("COMPLETED", ignoreCase = true) || dealStatus.equals("CANCELED", ignoreCase = true)) {
        throw ValidationException("This deal can no longer be paid")
    }
}

private fun JsonObject.stringOrThrow(key: String): String {
    return this[key]?.toString()?.trim('"')?.takeIf { it.isNotBlank() }
        ?: throw ValidationException("Missing chat meta field: $key")
}

private fun JsonObject.stringOrEmpty(key: String): String {
    return (this[key] as? JsonPrimitive)?.content?.trim().orEmpty()
}

private fun TradeOrder.toResponse(): TradeOrderResponse {
    return TradeOrderResponse(
        id = id,
        chatId = chatId,
        cardId = cardId,
        cardName = cardName,
        buyerUserId = buyerUserId,
        sellerUserId = sellerUserId,
        amountMinor = amountMinor,
        currency = currency,
        platformFeeMinor = platformFeeMinor,
        sellerAmountMinor = sellerAmountMinor,
        paymentStatus = paymentStatus.name,
        payoutStatus = payoutStatus.name,
        paidAt = paidAt,
        paidOutAt = paidOutAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
