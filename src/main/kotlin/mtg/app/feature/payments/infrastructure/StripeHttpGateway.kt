package mtg.app.feature.payments.infrastructure

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mtg.app.core.error.ConfigurationException
import mtg.app.core.error.ExternalServiceException
import mtg.app.feature.payments.domain.CheckoutSession
import mtg.app.feature.payments.domain.SellerPayoutStatus
import mtg.app.feature.payments.domain.StripeGateway
import mtg.app.feature.payments.domain.TradeOrder

class StripeHttpGateway(
    private val secretKey: String,
    private val webhookSecret: String,
    private val connectRefreshUrl: String,
    private val connectReturnUrl: String,
    private val checkoutSuccessUrl: String,
    private val checkoutCancelUrl: String,
    private val defaultCountry: String,
) : StripeGateway {
    private val client = HttpClient.newHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun createExpressAccount(email: String?): SellerPayoutStatus {
        ensureConfigured()
        val response = postForm(
            path = "/v1/accounts",
            form = listOfNotNull(
                "type" to "express",
                "country" to defaultCountry,
                email?.takeIf { it.isNotBlank() }?.let { "email" to it },
            )
        )
        return response.toSellerPayoutStatus()
    }

    override suspend fun refreshAccountStatus(accountId: String): SellerPayoutStatus {
        ensureConfigured()
        val response = getJson("/v1/accounts/$accountId")
        return response.toSellerPayoutStatus(accountId)
    }

    override suspend fun createAccountOnboardingLink(accountId: String): String {
        ensureConfigured()
        val response = postForm(
            path = "/v1/account_links",
            form = listOf(
                "account" to accountId,
                "refresh_url" to connectRefreshUrl,
                "return_url" to connectReturnUrl,
                "type" to "account_onboarding",
            )
        )
        return response.string("url")
    }

    override suspend fun createCheckoutSession(order: TradeOrder): CheckoutSession {
        ensureConfigured()
        val response = postForm(
            path = "/v1/checkout/sessions",
            form = listOf(
                "mode" to "payment",
                "success_url" to checkoutSuccessUrl,
                "cancel_url" to checkoutCancelUrl,
                "metadata[order_id]" to order.id,
                "line_items[0][quantity]" to "1",
                "line_items[0][price_data][currency]" to order.currency.lowercase(),
                "line_items[0][price_data][unit_amount]" to order.amountMinor.toString(),
                "line_items[0][price_data][product_data][name]" to order.cardName,
                "payment_intent_data[transfer_group]" to order.id,
            )
        )
        return CheckoutSession(
            sessionId = response.string("id"),
            url = response.string("url"),
        )
    }

    override suspend fun createTransfer(order: TradeOrder, destinationAccountId: String): String {
        ensureConfigured()
        val response = postForm(
            path = "/v1/transfers",
            form = listOf(
                "amount" to order.sellerAmountMinor.toString(),
                "currency" to order.currency.lowercase(),
                "destination" to destinationAccountId,
                "transfer_group" to order.id,
                "metadata[order_id]" to order.id,
            )
        )
        return response.string("id")
    }

    override fun verifyWebhookSignature(payload: String, signatureHeader: String?): Boolean {
        if (webhookSecret.isBlank()) return false
        val header = signatureHeader.orEmpty()
        val timestamp = header.split(',').firstOrNull { it.startsWith("t=") }?.substringAfter("t=") ?: return false
        val expected = computeHmacSha256("$timestamp.$payload", webhookSecret)
        val provided = header.split(',')
            .firstOrNull { it.startsWith("v1=") }
            ?.substringAfter("v1=")
            ?: return false
        return expected.equals(provided, ignoreCase = true)
    }

    private fun ensureConfigured() {
        if (secretKey.isBlank()) throw ConfigurationException("Stripe secret key is not configured")
    }

    private fun postForm(path: String, form: List<Pair<String, String>>): JsonObject {
        val body = form.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        val request = HttpRequest.newBuilder(URI("https://api.stripe.com$path"))
            .header("Authorization", "Bearer $secretKey")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        return parseStripeResponse(response)
    }

    private fun getJson(path: String): JsonObject {
        val request = HttpRequest.newBuilder(URI("https://api.stripe.com$path"))
            .header("Authorization", "Bearer $secretKey")
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        return parseStripeResponse(response)
    }

    private fun parseStripeResponse(response: HttpResponse<String>): JsonObject {
        if (response.statusCode() !in 200..299) {
            throw ExternalServiceException("Stripe request failed (${response.statusCode()}): ${response.body()}")
        }
        return json.parseToJsonElement(response.body()).jsonObject
    }

    private fun JsonObject.toSellerPayoutStatus(accountIdFallback: String? = null): SellerPayoutStatus {
        return SellerPayoutStatus(
            accountId = string("id").ifBlank { accountIdFallback },
            detailsSubmitted = boolean("details_submitted"),
            chargesEnabled = boolean("charges_enabled"),
            payoutsEnabled = boolean("payouts_enabled"),
        )
    }

    private fun JsonObject.string(key: String): String {
        return this[key]?.jsonPrimitive?.content.orEmpty()
    }

    private fun JsonObject.boolean(key: String): Boolean {
        return this[key]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun computeHmacSha256(payload: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
