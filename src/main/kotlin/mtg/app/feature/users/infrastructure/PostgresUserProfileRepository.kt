package mtg.app.feature.users.infrastructure

import mtg.app.core.error.ValidationException
import mtg.app.feature.users.domain.UserProfileRepository
import kotlin.time.Clock
import javax.sql.DataSource

class PostgresUserProfileRepository(
    private val dataSource: DataSource,
) : UserProfileRepository {
    override suspend fun upsertNickname(userId: String, nickname: String) {
        val normalized = normalizeNickname(nickname)
        if (normalized.isBlank()) throw ValidationException("nickname is required")

        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val previousNickname = connection.prepareStatement(
                    "SELECT nickname FROM user_profiles WHERE user_id = ?"
                ).use { statement ->
                    statement.setString(1, userId)
                    statement.executeQuery().use { rs -> if (rs.next()) rs.getString("nickname") else null }
                }

                val previousNormalized = previousNickname?.let(::normalizeNickname)
                val now = Clock.System.now().toEpochMilliseconds()

                connection.prepareStatement(
                    """
                    INSERT INTO nickname_registry (normalized_nickname, uid, updated_at)
                    VALUES (?, ?, ?)
                    ON CONFLICT (normalized_nickname) DO NOTHING
                    """.trimIndent()
                ).use { statement ->
                    statement.setString(1, normalized)
                    statement.setString(2, userId)
                    statement.setLong(3, now)
                    statement.executeUpdate()
                }

                val owner = connection.prepareStatement(
                    "SELECT uid FROM nickname_registry WHERE normalized_nickname = ?"
                ).use { statement ->
                    statement.setString(1, normalized)
                    statement.executeQuery().use { rs -> if (rs.next()) rs.getString("uid") else null }
                }

                if (owner != null && owner != userId) {
                    throw ValidationException("nickname is already taken")
                }

                if (owner == userId) {
                    connection.prepareStatement(
                        "UPDATE nickname_registry SET updated_at = ? WHERE normalized_nickname = ?"
                    ).use { statement ->
                        statement.setLong(1, now)
                        statement.setString(2, normalized)
                        statement.executeUpdate()
                    }
                }

                connection.prepareStatement(
                    """
                    INSERT INTO user_profiles (user_id, nickname, updated_at)
                    VALUES (?, ?, ?)
                    ON CONFLICT (user_id)
                    DO UPDATE SET nickname = EXCLUDED.nickname, updated_at = EXCLUDED.updated_at
                    """.trimIndent()
                ).use { statement ->
                    statement.setString(1, userId)
                    statement.setString(2, nickname)
                    statement.setLong(3, now)
                    statement.executeUpdate()
                }

                if (!previousNormalized.isNullOrBlank() && previousNormalized != normalized) {
                    connection.prepareStatement(
                        "DELETE FROM nickname_registry WHERE normalized_nickname = ? AND uid = ?"
                    ).use { statement ->
                        statement.setString(1, previousNormalized)
                        statement.setString(2, userId)
                        statement.executeUpdate()
                    }
                }

                connection.commit()
            } catch (t: Throwable) {
                runCatching { connection.rollback() }
                throw t
            } finally {
                connection.autoCommit = true
            }
        }
    }

    override suspend fun loadNickname(userId: String): String? {
        val sql = """
            SELECT nickname
            FROM user_profiles
            WHERE user_id = ?
        """.trimIndent()

        return dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, userId)
                statement.executeQuery().use { rs ->
                    if (rs.next()) rs.getString("nickname") else null
                }
            }
        }
    }

    override suspend fun loadNicknames(userIds: Set<String>): Map<String, String> {
        if (userIds.isEmpty()) return emptyMap()

        val placeholders = List(userIds.size) { "?" }.joinToString(",")
        val sql = "SELECT user_id, nickname FROM user_profiles WHERE user_id IN ($placeholders)"

        return buildMap {
            dataSource.connection.use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    userIds.forEachIndexed { index, userId ->
                        statement.setString(index + 1, userId)
                    }
                    statement.executeQuery().use { rs ->
                        while (rs.next()) {
                            put(
                                rs.getString("user_id"),
                                rs.getString("nickname"),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun normalizeNickname(value: String): String {
        return value.trim().lowercase()
    }
}
