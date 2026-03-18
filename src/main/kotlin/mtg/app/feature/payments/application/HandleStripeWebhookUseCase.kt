package mtg.app.feature.payments.application

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import mtg.app.core.error.ForbiddenException
import mtg.app.feature.bridge.infrastructure.ChatRouteStore
import mtg.app.feature.payments.domain.PaymentStatus
import mtg.app.feature.payments.domain.PaymentsRepository
import mtg.app.feature.payments.domain.StripeGateway

class HandleStripeWebhookUseCase(
    private val repository: PaymentsRepository,
    private val stripeGateway: StripeGateway,
    private val chatStore: ChatRouteStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend operator fun invoke(payload: String, signatureHeader: String?) {
        if (!stripeGateway.verifyWebhookSignature(payload, signatureHeader)) {
            throw ForbiddenException("Invalid Stripe webhook signature")
        }

        val root = json.parseToJsonElement(payload).jsonObject
        val eventId = root.string("id")
        val eventType = root.string("type")
        if (eventId.isBlank() || eventType.isBlank()) return

        val dataObject = root["data"]?.jsonObject?.get("object")?.jsonObject ?: return
        val orderId = dataObject.extractOrderId()
        val recorded = repository.recordWebhookEvent(
            eventId = eventId,
            eventType = eventType,
            orderId = orderId,
            payload = payload,
            receivedAt = System.currentTimeMillis(),
        )
        if (!recorded) return

        when (eventType) {
            "checkout.session.completed" -> handleCheckoutCompleted(orderId = orderId, dataObject = dataObject)
            "checkout.session.expired" -> handlePaymentStatus(orderId = orderId, status = PaymentStatus.FAILED)
            "payment_intent.payment_failed" -> handlePaymentStatus(orderId = orderId, status = PaymentStatus.FAILED)
            "charge.refunded" -> handlePaymentStatus(orderId = orderId, status = PaymentStatus.REFUNDED)
            "account.updated" -> handleAccountUpdated(dataObject)
            else -> Unit
        }
    }

    private suspend fun handleCheckoutCompleted(orderId: String, dataObject: JsonObject) {
        if (orderId.isBlank()) return
        val paymentIntentId = dataObject.string("payment_intent").ifBlank { null }
        val order = repository.markOrderPaid(
            orderId = orderId,
            paymentIntentId = paymentIntentId,
            paidAt = System.currentTimeMillis(),
        ) ?: return

        val chatMeta = chatStore.getChatMeta(order.chatId) ?: return
        val currentStatus = chatMeta.string("dealStatus")
        if (currentStatus.equals("COMPLETED", ignoreCase = true) || currentStatus.equals("CANCELED", ignoreCase = true)) {
            return
        }

        chatStore.patchChatMeta(
            chatId = order.chatId,
            patch = buildJsonObject {
                put("dealStatus", "PROPOSED")
                put("buyerConfirmed", true)
                put("sellerConfirmed", true)
                put("buyerCompleted", chatMeta.boolean("buyerCompleted"))
                put("sellerCompleted", chatMeta.boolean("sellerCompleted"))
            },
        )
    }

    private suspend fun handlePaymentStatus(orderId: String, status: PaymentStatus) {
        if (orderId.isBlank()) return
        repository.updateOrderPaymentStatus(
            orderId = orderId,
            status = status,
            updatedAt = System.currentTimeMillis(),
        )
    }

    private suspend fun handleAccountUpdated(dataObject: JsonObject) {
        val accountId = dataObject.string("id")
        if (accountId.isBlank()) return
        val existing = repository.findSellerPayoutAccountByConnectedAccountId(accountId) ?: return
        val refreshed = stripeGateway.refreshAccountStatus(accountId)
        repository.saveSellerPayoutAccount(
            existing.copy(
                detailsSubmitted = refreshed.detailsSubmitted,
                chargesEnabled = refreshed.chargesEnabled,
                payoutsEnabled = refreshed.payoutsEnabled,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    private fun JsonObject.extractOrderId(): String {
        val metadataOrderId = this["metadata"]?.jsonObject?.string("order_id").orEmpty()
        if (metadataOrderId.isNotBlank()) return metadataOrderId
        return string("transfer_group")
    }

    private fun JsonObject.string(key: String): String {
        return this[key]?.jsonPrimitive?.content.orEmpty()
    }

    private fun JsonObject.boolean(key: String): Boolean {
        return this[key]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
    }
}
