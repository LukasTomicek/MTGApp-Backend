package mtg.app.feature.users.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import mtg.app.feature.users.application.LoadUserNicknameUseCase
import mtg.app.feature.users.application.SaveUserNicknameUseCase

@Serializable
data class UpsertProfileRequest(
    val userId: String,
    val nickname: String,
)

@Serializable
private data class UserProfileResponse(
    val userId: String,
    val nickname: String?,
)

fun Route.registerUserProfileRoutes(
    saveUserNickname: SaveUserNicknameUseCase,
    loadUserNickname: LoadUserNicknameUseCase,
) {
    route("/v1/users/profile") {
        put {
            val request = call.receive<UpsertProfileRequest>()
            saveUserNickname(
                userId = request.userId,
                nickname = request.nickname,
            )
            call.respond(HttpStatusCode.OK)
        }

        get {
            val userId = call.request.queryParameters["userId"].orEmpty()
            call.respond(
                UserProfileResponse(
                    userId = userId,
                    nickname = if (userId.isBlank()) null else loadUserNickname(userId = userId),
                )
            )
        }

        get("/{userId}") {
            val userId = call.parameters["userId"].orEmpty()
            val nickname = loadUserNickname(userId = userId)
            call.respond(
                UserProfileResponse(
                    userId = userId,
                    nickname = nickname,
                )
            )
        }
    }
}
