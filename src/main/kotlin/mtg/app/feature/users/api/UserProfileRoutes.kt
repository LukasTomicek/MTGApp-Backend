package mtg.app.feature.users.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import mtg.app.core.auth.FirebaseAuthVerifier
import mtg.app.core.auth.requireFirebasePrincipal
import mtg.app.feature.payments.application.LoadSellerBalanceUseCase
import mtg.app.feature.users.application.LoadUserNicknameUseCase
import mtg.app.feature.users.application.SaveUserNicknameUseCase

@Serializable
data class UpsertProfileRequest(
    val nickname: String,
)

@Serializable
private data class PublicUserProfileResponse(
    val userId: String,
    val nickname: String?,
)

@Serializable
private data class OwnUserProfileResponse(
    val userId: String,
    val nickname: String?,
    val balanceMinor: Long = 0L,
)

fun Route.registerUserProfileRoutes(
    authVerifier: FirebaseAuthVerifier,
    saveUserNickname: SaveUserNicknameUseCase,
    loadUserNickname: LoadUserNicknameUseCase,
    loadSellerBalance: LoadSellerBalanceUseCase,
) {
    route("/v1/users/profile") {
        get("/{userId}") {
            val userId = call.parameters["userId"].orEmpty()
            val nickname = loadUserNickname(userId = userId)
            call.respond(
                PublicUserProfileResponse(
                    userId = userId,
                    nickname = nickname,
                )
            )
        }
    }

    route("/v1/users/me/profile") {
        get {
            val principal = call.requireFirebasePrincipal(authVerifier)
            val nickname = loadUserNickname(userId = principal.uid)
            val balanceMinor = loadSellerBalance(userId = principal.uid)
            call.respond(
                OwnUserProfileResponse(
                    userId = principal.uid,
                    nickname = nickname,
                    balanceMinor = balanceMinor,
                )
            )
        }

        put {
            val principal = call.requireFirebasePrincipal(authVerifier)
            val request = call.receive<UpsertProfileRequest>()
            saveUserNickname(
                userId = principal.uid,
                nickname = request.nickname,
            )
            call.respond(HttpStatusCode.OK)
        }
    }
}
