package mtg.app.feature.bridge.infrastructure

import kotlinx.serialization.json.JsonObject
import javax.sql.DataSource

class PostgresRatingStore(
    private val dataSource: DataSource,
    private val support: PostgresDocumentStoreSupport,
) {
    fun hasRatedChat(uid: String, chatId: String): Boolean {
        val given = support.readUserSection(uid, UserSection.GIVEN_RATINGS)
        return given[chatId] != null
    }

    fun listReceivedRatings(uid: String): JsonObject = support.readUserSection(uid, UserSection.RECEIVED_RATINGS)

    fun saveGivenRating(uid: String, chatId: String, payload: JsonObject) {
        support.upsertUserSectionEntry(uid, UserSection.GIVEN_RATINGS, chatId, payload)
    }

    fun saveReceivedRating(uid: String, ratingId: String, payload: JsonObject) {
        support.upsertUserSectionEntry(uid, UserSection.RECEIVED_RATINGS, ratingId, payload)
    }

    fun updateUserProfileRating(uid: String, average: Double, count: Int) {
        val now = support.nowMillis()
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO user_documents (uid, updated_at, rating_average, rating_count)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (uid)
                DO UPDATE SET updated_at = EXCLUDED.updated_at,
                              rating_average = EXCLUDED.rating_average,
                              rating_count = EXCLUDED.rating_count
                """.trimIndent()
            ).use { st ->
                st.setString(1, uid)
                st.setLong(2, now)
                st.setDouble(3, average)
                st.setInt(4, count.coerceAtLeast(0))
                st.executeUpdate()
            }

            connection.prepareStatement(
                """
                UPDATE user_profiles
                SET rating_average = ?, rating_count = ?
                WHERE user_id = ?
                """.trimIndent()
            ).use { st ->
                st.setDouble(1, average)
                st.setInt(2, count.coerceAtLeast(0))
                st.setString(3, uid)
                st.executeUpdate()
            }
        }
    }
}
