package mtg.app.feature.bridge.infrastructure

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock
import javax.sql.DataSource

enum class UserSection(val column: String) {
    COLLECTION("collection"),
    MAP_PINS("map_pins"),
    MATCHES("matches"),
    NOTIFICATIONS("notifications"),
    GIVEN_RATINGS("given_ratings"),
    RECEIVED_RATINGS("received_ratings"),
}

class PostgresDocumentStoreSupport(
    private val dataSource: DataSource,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun ensureUserDocument(uid: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO user_documents (uid, updated_at)
                VALUES (?, ?)
                ON CONFLICT (uid) DO NOTHING
                """.trimIndent()
            ).use { st ->
                st.setString(1, uid)
                st.setLong(2, nowMillis())
                st.executeUpdate()
            }
        }
    }

    fun readUserSection(uid: String, section: UserSection): JsonObject {
        ensureUserDocument(uid)
        val sql = "SELECT ${section.column} FROM user_documents WHERE uid = ?"
        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { st ->
                st.setString(1, uid)
                st.executeQuery().use { rs ->
                    if (!rs.next()) return JsonObject(emptyMap())
                    return parseJsonObject(rs.getString(section.column))
                }
            }
        }
    }

    fun writeUserSection(uid: String, section: UserSection, payload: JsonObject) {
        ensureUserDocument(uid)
        val sql = "UPDATE user_documents SET ${section.column} = ?::jsonb, updated_at = ? WHERE uid = ?"
        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { st ->
                st.setString(1, payload.toString())
                st.setLong(2, nowMillis())
                st.setString(3, uid)
                st.executeUpdate()
            }
        }
    }

    fun upsertUserSectionEntry(uid: String, section: UserSection, key: String, payload: JsonObject) {
        val current = readUserSection(uid, section)
        val next = buildJsonObject {
            current.forEach { (k, v) -> put(k, v) }
            put(key, payload)
        }
        writeUserSection(uid, section, next)
    }

    fun deleteUserSectionEntry(uid: String, section: UserSection, key: String) {
        val current = readUserSection(uid, section)
        if (!current.containsKey(key)) return
        val next = JsonObject(current.filterKeys { it != key })
        writeUserSection(uid, section, next)
    }

    fun ensureChatDocument(chatId: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO chat_documents (chat_id, updated_at)
                VALUES (?, ?)
                ON CONFLICT (chat_id) DO NOTHING
                """.trimIndent()
            ).use { st ->
                st.setString(1, chatId)
                st.setLong(2, nowMillis())
                st.executeUpdate()
            }
        }
    }

    fun parseJsonObject(raw: String?): JsonObject {
        if (raw.isNullOrBlank()) return JsonObject(emptyMap())
        return runCatching { json.parseToJsonElement(raw) as? JsonObject }
            .getOrNull()
            ?: JsonObject(emptyMap())
    }

    fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
}
