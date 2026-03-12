package mtg.app.core.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mtg.app.core.error.ForbiddenException
import mtg.app.core.error.UnauthorizedException
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.PublicKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

data class FirebasePrincipal(
    val uid: String,
    val email: String?,
)

class FirebaseAuthVerifier(
    private val projectId: String,
) {
    private val httpClient = HttpClient.newBuilder().build()
    @Volatile
    private var certCache: CertCache? = null

    fun verifyBearerToken(authorizationHeader: String?): FirebasePrincipal {
        val token = authorizationHeader
            ?.trim()
            ?.takeIf { it.startsWith("Bearer ") }
            ?.removePrefix("Bearer ")
            ?.trim()
            .orEmpty()
        if (token.isBlank()) throw UnauthorizedException("Missing bearer token")
        return verifyIdToken(token)
    }

    fun verifyIdToken(idToken: String): FirebasePrincipal {
        val parts = idToken.split(".")
        if (parts.size != 3) throw UnauthorizedException("Invalid Firebase token format")

        val headerJson = decodeJson(parts[0])
        val payloadJson = decodeJson(parts[1])
        val kid = headerJson.string("kid")?.trim().orEmpty()
        if (kid.isBlank()) throw UnauthorizedException("Missing token key id")

        val nowSeconds = Instant.now().epochSecond
        val audience = payloadJson.string("aud")?.trim().orEmpty()
        val issuer = payloadJson.string("iss")?.trim().orEmpty()
        val subject = payloadJson.string("sub")?.trim().orEmpty()
        val expiresAt = payloadJson.long("exp") ?: 0L
        val issuedAt = payloadJson.long("iat") ?: 0L

        if (audience != projectId) throw UnauthorizedException("Invalid token audience")
        if (issuer != "https://securetoken.google.com/$projectId") {
            throw UnauthorizedException("Invalid token issuer")
        }
        if (subject.isBlank()) throw UnauthorizedException("Invalid token subject")
        if (expiresAt <= nowSeconds) throw UnauthorizedException("Token expired")
        if (issuedAt > nowSeconds + 60) throw UnauthorizedException("Invalid token issue time")

        val publicKey = loadPublicKeys()[kid] ?: throw UnauthorizedException("Unknown Firebase signing key")
        val signature = Base64.getUrlDecoder().decode(parts[2])
        val signingInput = "${parts[0]}.${parts[1]}".toByteArray(Charsets.UTF_8)
        val verified = Signature.getInstance("SHA256withRSA").run {
            initVerify(publicKey)
            update(signingInput)
            verify(signature)
        }
        if (!verified) throw UnauthorizedException("Invalid token signature")

        return FirebasePrincipal(
            uid = subject,
            email = payloadJson.string("email")?.trim()?.takeIf { it.isNotBlank() },
        )
    }

    fun requireSameUser(requestedUserId: String?, principal: FirebasePrincipal): String {
        val normalized = requestedUserId?.trim().orEmpty()
        if (normalized.isNotBlank() && normalized != principal.uid) {
            throw ForbiddenException("Requested user does not match authenticated user")
        }
        return principal.uid
    }

    private fun loadPublicKeys(): Map<String, PublicKey> {
        val cached = certCache
        val nowMillis = System.currentTimeMillis()
        if (cached != null && cached.expiresAtMillis > nowMillis) {
            return cached.keys
        }

        synchronized(this) {
            val secondCheck = certCache
            if (secondCheck != null && secondCheck.expiresAtMillis > nowMillis) {
                return secondCheck.keys
            }

            val request = HttpRequest.newBuilder()
                .uri(URI.create(FIREBASE_CERTS_URL))
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                throw UnauthorizedException("Unable to load Firebase signing certificates")
            }

            val parsed = Json.parseToJsonElement(response.body()) as? JsonObject
                ?: throw UnauthorizedException("Invalid Firebase certificate response")
            val keys = ConcurrentHashMap<String, PublicKey>()
            parsed.forEach { (keyId, value) ->
                val pem = (value as? JsonPrimitive)?.content?.trim().orEmpty()
                if (pem.isNotBlank()) {
                    keys[keyId] = parsePublicKeyFromPem(pem)
                }
            }
            if (keys.isEmpty()) {
                throw UnauthorizedException("No Firebase signing certificates available")
            }

            val maxAgeSeconds = parseMaxAgeSeconds(response.headers().firstValue("Cache-Control").orElse(null))
            certCache = CertCache(
                keys = keys,
                expiresAtMillis = nowMillis + (maxAgeSeconds * 1000L),
            )
            return keys
        }
    }

    private fun decodeJson(part: String): JsonObject {
        val decoded = Base64.getUrlDecoder().decode(part)
        val raw = decoded.toString(Charsets.UTF_8)
        return Json.parseToJsonElement(raw) as? JsonObject
            ?: throw UnauthorizedException("Invalid Firebase token payload")
    }

    private fun parsePublicKeyFromPem(pem: String): PublicKey {
        val factory = CertificateFactory.getInstance("X.509")
        val certificate = factory.generateCertificate(
            ByteArrayInputStream(pem.toByteArray(Charsets.UTF_8))
        ) as X509Certificate
        return certificate.publicKey
    }

    private fun parseMaxAgeSeconds(cacheControl: String?): Long {
        val maxAgeToken = cacheControl
            ?.split(",")
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("max-age=") }
            ?.substringAfter("max-age=")
            ?.toLongOrNull()
        return maxAgeToken ?: DEFAULT_CERT_CACHE_SECONDS
    }

    private fun JsonObject.string(key: String): String? {
        return (this[key] as? JsonPrimitive)?.content
    }

    private fun JsonObject.long(key: String): Long? {
        return (this[key] as? JsonPrimitive)?.content?.toLongOrNull()
    }

    private data class CertCache(
        val keys: Map<String, PublicKey>,
        val expiresAtMillis: Long,
    )

    private companion object {
        const val FIREBASE_CERTS_URL =
            "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com"
        const val DEFAULT_CERT_CACHE_SECONDS = 3600L
    }
}
