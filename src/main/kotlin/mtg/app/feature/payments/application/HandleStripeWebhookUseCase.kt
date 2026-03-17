package mtg.app.feature.payments.application

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mtg.app.core.error.ForbiddenException
import mtg.app.feature.payments.domain.PaymentsRepository
import mtg.app.feature.payments.domain.StripeGateway

class HandleStripeWebhookUseCase(
    private val repository: PaymentsRepository,
    private val stripeGateway: StripeGateway,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend operator fun invoke(payload: String, signatureHeader: String?) {
        if (!stripeGateway.verifyWebhookSignature(payload, signatureHeader)) {
            throw ForbiddenException("Invalid Stripe webhook signature")
        }

        val root = json.parseToJsonElement(payload).jsonObject
        when (root.string("type")) {
            "checkout.session.completed" -> handleCheckoutCompleted(root)
            else -> Unit
        }
    }

    private suspend fun handleCheckoutCompleted(root: JsonObject) {
        val dataObject = root["data"]?.jsonObject?.get("object")?.jsonObject ?: return
        val metadata = dataObject["metadata"]?.jsonObject ?: return
        val orderId = metadata.string("order_id")
        if (orderId.isBlank()) return
        val paymentIntentId = dataObject.string("payment_intent").ifBlank { null }
        repository.markOrderPaid(orderId = orderId, paymentIntentId = paymentIntentId, paidAt = System.currentTimeMillis())
    }

    private fun JsonObject.string(key: String): String {
        return this[key]?.jsonPrimitive?.content.orEmpty()
    }
}
