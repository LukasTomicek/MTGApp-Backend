package mtg.app.feature.wallet.infrastructure

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import mtg.app.core.error.ConfigurationException
import mtg.app.core.error.ExternalServiceException
import mtg.app.core.error.ValidationException
import mtg.app.feature.wallet.domain.VerifiedWalletPurchase
import mtg.app.feature.wallet.domain.WalletPlatform

class GooglePlayPurchaseVerifier(
    packageName: String,
    serviceAccountEmail: String,
    serviceAccountPrivateKey: String,
) {
    private val packageName = packageName.trim()
    private val serviceAccountEmail = serviceAccountEmail.trim()
    private val serviceAccountPrivateKey = serviceAccountPrivateKey.trim()
    private val httpClient = HttpClient.newHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    init {
        if (this.packageName.isBlank()) throw ConfigurationException("GOOGLE_PLAY_PACKAGE_NAME is missing")
        if (this.serviceAccountEmail.isBlank()) throw ConfigurationException("GOOGLE_PLAY_SERVICE_ACCOUNT_EMAIL is missing")
        if (this.serviceAccountPrivateKey.isBlank()) throw ConfigurationException("GOOGLE_PLAY_SERVICE_ACCOUNT_PRIVATE_KEY is missing")
    }

    suspend fun verify(productId: String, purchaseToken: String): VerifiedWalletPurchase {
        val accessToken = requestAccessToken()
        val responseBody = getPurchase(accessToken = accessToken, purchaseToken = purchaseToken)
        val root = json.parseToJsonElement(responseBody) as? JsonObject
            ?: throw ExternalServiceException("Google Play verification returned invalid payload")

        val purchaseState = root.stringAt("purchaseStateContext", "purchaseState")
        if (purchaseState != "PURCHASED") {
            throw ValidationException("Google Play purchase is not completed")
        }

        val lineItems = root["productLineItem"]?.let { it as? kotlinx.serialization.json.JsonArray }
            ?: throw ExternalServiceException("Google Play verification missing line items")
        val matched = lineItems.any { item ->
            (item as? JsonObject)?.get("productId")?.jsonPrimitive?.contentOrNull == productId
        }
        if (!matched) {
            throw ValidationException("Google Play purchase does not match requested product")
        }

        val canonicalTransactionId = root["orderId"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifBlank { purchaseToken }

        return VerifiedWalletPurchase(
            platform = WalletPlatform.ANDROID,
            productId = productId,
            storeTransactionId = canonicalTransactionId,
            purchaseToken = purchaseToken,
        )
    }

    private fun requestAccessToken(): String {
        val now = Instant.now().epochSecond
        val jwt = JwtSupport.createRs256Jwt(
            claims = mapOf(
                "iss" to serviceAccountEmail,
                "scope" to "https://www.googleapis.com/auth/androidpublisher",
                "aud" to "https://oauth2.googleapis.com/token",
                "iat" to now,
                "exp" to (now + 3600),
            ),
            privateKeyPem = serviceAccountPrivateKey,
        )

        val form = buildString {
            append("grant_type=")
            append(urlEncode("urn:ietf:params:oauth:grant-type:jwt-bearer"))
            append('&')
            append("assertion=")
            append(urlEncode(jwt))
        }

        val request = HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw ExternalServiceException("Failed to obtain Google Play access token (${response.statusCode()})")
        }
        val root = json.parseToJsonElement(response.body()) as? JsonObject
            ?: throw ExternalServiceException("Google Play token response was invalid")
        return root["access_token"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            .ifBlank { throw ExternalServiceException("Google Play access token missing") }
    }

    private fun getPurchase(accessToken: String, purchaseToken: String): String {
        val url = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/$packageName/purchases/productsv2/tokens/${urlEncode(purchaseToken)}"
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("Authorization", "Bearer $accessToken")
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() == 404) {
            throw ValidationException("Google Play purchase token not found")
        }
        if (response.statusCode() !in 200..299) {
            throw ExternalServiceException("Google Play purchase verification failed (${response.statusCode()})")
        }
        return response.body()
    }

    private fun JsonObject.stringAt(parentKey: String, childKey: String): String? {
        return (get(parentKey) as? JsonObject)
            ?.get(childKey)
            ?.let { it as? JsonPrimitive }
            ?.contentOrNull
            ?.trim()
            ?.takeUnless { it.isBlank() }
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)
}
