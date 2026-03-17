package mtg.app.feature.wallet.infrastructure

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import mtg.app.core.error.ConfigurationException
import mtg.app.core.error.ExternalServiceException
import mtg.app.core.error.ValidationException
import mtg.app.feature.wallet.domain.VerifiedWalletPurchase
import mtg.app.feature.wallet.domain.WalletPlatform

class AppleAppStorePurchaseVerifier(
    issuerId: String,
    keyId: String,
    privateKey: String,
    bundleId: String,
) {
    private val issuerId = issuerId.trim()
    private val keyId = keyId.trim()
    private val privateKey = privateKey.trim()
    private val bundleId = bundleId.trim()
    private val httpClient = HttpClient.newHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    init {
        if (this.issuerId.isBlank()) throw ConfigurationException("APPLE_APP_STORE_ISSUER_ID is missing")
        if (this.keyId.isBlank()) throw ConfigurationException("APPLE_APP_STORE_KEY_ID is missing")
        if (this.privateKey.isBlank()) throw ConfigurationException("APPLE_APP_STORE_PRIVATE_KEY is missing")
        if (this.bundleId.isBlank()) throw ConfigurationException("APPLE_BUNDLE_ID is missing")
    }

    suspend fun verify(productId: String, transactionId: String): VerifiedWalletPurchase {
        val signedJwt = createBearerJwt()
        val signedTransaction = loadSignedTransaction(
            bearerToken = signedJwt,
            transactionId = transactionId,
            productId = productId,
        )
        val payload = JwtSupport.decodeJwtPayload(signedTransaction)
        val root = json.parseToJsonElement(payload) as? JsonObject
            ?: throw ExternalServiceException("Apple verification returned invalid transaction payload")

        val resolvedProductId = root["productId"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (resolvedProductId != productId) {
            throw ValidationException("Apple purchase does not match requested product")
        }

        val resolvedBundleId = root["bundleId"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (resolvedBundleId != bundleId) {
            throw ValidationException("Apple purchase belongs to a different bundle")
        }

        val revoked = root["revocationDate"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().isNotBlank()
        if (revoked) {
            throw ValidationException("Apple purchase was revoked")
        }

        val resolvedTransactionId = root["transactionId"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            .ifBlank { transactionId }

        return VerifiedWalletPurchase(
            platform = WalletPlatform.IOS,
            productId = productId,
            storeTransactionId = resolvedTransactionId,
            purchaseToken = null,
        )
    }

    private fun createBearerJwt(): String {
        val now = Instant.now().epochSecond
        return JwtSupport.createEs256Jwt(
            keyId = keyId,
            claims = mapOf(
                "iss" to issuerId,
                "iat" to now,
                "exp" to (now + 300),
                "aud" to "appstoreconnect-v1",
                "bid" to bundleId,
            ),
            privateKeyPem = privateKey,
        )
    }

    private fun loadSignedTransaction(
        bearerToken: String,
        transactionId: String,
        productId: String,
    ): String {
        val query = "productId=${urlEncode(productId)}&productType=CONSUMABLE&revoked=false&sort=DESCENDING"
        val prodUrl = "https://api.storekit.itunes.apple.com/inApps/v2/history/${urlEncode(transactionId)}?$query"
        val sandboxUrl = "https://api.storekit-sandbox.itunes.apple.com/inApps/v2/history/${urlEncode(transactionId)}?$query"

        return requestHistory(url = prodUrl, bearerToken = bearerToken)
            ?: requestHistory(url = sandboxUrl, bearerToken = bearerToken)
            ?: throw ValidationException("Apple transaction not found")
    }

    private fun requestHistory(url: String, bearerToken: String): String? {
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("Authorization", "Bearer $bearerToken")
            .header("Accept", "application/json")
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() == 404) return null
        if (response.statusCode() !in 200..299) {
            throw ExternalServiceException("Apple purchase verification failed (${response.statusCode()})")
        }
        val root = json.parseToJsonElement(response.body()) as? JsonObject
            ?: throw ExternalServiceException("Apple verification returned invalid payload")
        val transactions = root["signedTransactions"] as? JsonArray
            ?: throw ExternalServiceException("Apple verification missing signed transactions")
        return transactions.firstOrNull()?.jsonPrimitive?.contentOrNull?.trim()?.takeUnless { it.isBlank() }
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)
}
