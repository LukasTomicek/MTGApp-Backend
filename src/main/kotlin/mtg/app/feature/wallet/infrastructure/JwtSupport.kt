package mtg.app.feature.wallet.infrastructure

import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal object JwtSupport {
    private val json = Json { encodeDefaults = true }
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    fun createRs256Jwt(
        headerKid: String? = null,
        claims: Map<String, Any>,
        privateKeyPem: String,
    ): String {
        val header = buildJsonObject {
            put("alg", "RS256")
            put("typ", "JWT")
            headerKid?.takeIf { it.isNotBlank() }?.let { put("kid", it) }
        }
        val signingInput = encode(header.toString()) + "." + encode(json.encodeToString(MapSerializer, claims))
        val privateKey = parsePrivateKey(privateKeyPem, algorithm = "RSA")
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(privateKey)
            update(signingInput.toByteArray(Charsets.UTF_8))
            sign()
        }
        return signingInput + "." + encoder.encodeToString(signature)
    }

    fun createEs256Jwt(
        keyId: String,
        claims: Map<String, Any>,
        privateKeyPem: String,
    ): String {
        val header = buildJsonObject {
            put("alg", "ES256")
            put("typ", "JWT")
            put("kid", keyId)
        }
        val signingInput = encode(header.toString()) + "." + encode(json.encodeToString(MapSerializer, claims))
        val privateKey = parsePrivateKey(privateKeyPem, algorithm = "EC")
        val derSignature = Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(signingInput.toByteArray(Charsets.UTF_8))
            sign()
        }
        return signingInput + "." + encoder.encodeToString(derToJoseSignature(derSignature, 32))
    }

    fun decodeJwtPayload(jwt: String): String {
        val parts = jwt.split('.')
        require(parts.size >= 2) { "Invalid JWT" }
        return String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
    }

    private fun encode(value: String): String {
        return encoder.encodeToString(value.toByteArray(Charsets.UTF_8))
    }

    private fun parsePrivateKey(pem: String, algorithm: String): PrivateKey {
        val normalized = pem
            .replace("\\n", "\n")
            .lineSequence()
            .filterNot { it.startsWith("-----") }
            .joinToString(separator = "")
            .trim()
        val keyBytes = Base64.getDecoder().decode(normalized)
        return KeyFactory.getInstance(algorithm).generatePrivate(PKCS8EncodedKeySpec(keyBytes))
    }

    private fun derToJoseSignature(der: ByteArray, outputSize: Int): ByteArray {
        require(der.isNotEmpty() && der[0] == 0x30.toByte()) { "Invalid DER signature" }
        var offset = 2
        require(der[offset] == 0x02.toByte()) { "Invalid DER signature" }
        val rLength = der[offset + 1].toInt() and 0xFF
        val r = der.copyOfRange(offset + 2, offset + 2 + rLength)
        offset += 2 + rLength
        require(der[offset] == 0x02.toByte()) { "Invalid DER signature" }
        val sLength = der[offset + 1].toInt() and 0xFF
        val s = der.copyOfRange(offset + 2, offset + 2 + sLength)
        return padTo(r, outputSize) + padTo(s, outputSize)
    }

    private fun padTo(input: ByteArray, size: Int): ByteArray {
        val trimmed = input.dropWhile { it == 0.toByte() }.toByteArray()
        return if (trimmed.size >= size) {
            trimmed.takeLast(size).toByteArray()
        } else {
            ByteArray(size - trimmed.size) + trimmed
        }
    }

    private object MapSerializer : kotlinx.serialization.KSerializer<Map<String, Any>> {
        override val descriptor = kotlinx.serialization.json.JsonObject.serializer().descriptor

        override fun serialize(
            encoder: kotlinx.serialization.encoding.Encoder,
            value: Map<String, Any>,
        ) {
            val jsonEncoder = encoder as? kotlinx.serialization.json.JsonEncoder
                ?: error("JsonEncoder required")
            val element = buildJsonObject {
                value.forEach { (key, raw) ->
                    when (raw) {
                        is String -> put(key, raw)
                        is Number -> put(key, raw.toString())
                        is Boolean -> put(key, raw)
                        else -> error("Unsupported JWT claim type for key '$key'")
                    }
                }
            }
            jsonEncoder.encodeJsonElement(element)
        }

        override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): Map<String, Any> {
            error("Not supported")
        }
    }
}
