package mtg.app.feature.users.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mtg.app.core.auth.FirebaseAuthVerifier
import mtg.app.core.auth.requireFirebasePrincipal
import mtg.app.feature.bridge.infrastructure.PostgresNotificationStore

@Serializable
private data class CreateNotificationRequest(
    val recipientUid: String,
    val payload: JsonObject,
)

fun Route.registerUserNotificationRoutes(
    authVerifier: FirebaseAuthVerifier,
    notificationStore: PostgresNotificationStore,
) {
    route("/v1/users/me/notifications") {
        get {
            val principal = call.requireFirebasePrincipal(authVerifier)
            call.respond(notificationStore.listNotifications(principal.uid))
        }

        get("/unread") {
            val principal = call.requireFirebasePrincipal(authVerifier)
            call.respond(buildJsonObject {
                put("hasUnread", notificationStore.hasUnreadNotifications(principal.uid))
            })
        }

        patch("/{notificationId}/read") {
            val principal = call.requireFirebasePrincipal(authVerifier)
            val notificationId = call.parameters["notificationId"].orEmpty()
            notificationStore.markNotificationRead(principal.uid, notificationId)
            call.respond(HttpStatusCode.OK)
        }

        delete("/{notificationId}") {
            val principal = call.requireFirebasePrincipal(authVerifier)
            val notificationId = call.parameters["notificationId"].orEmpty()
            notificationStore.deleteNotification(principal.uid, notificationId)
            call.respond(HttpStatusCode.OK)
        }
    }

    route("/v1/notifications") {
        post {
            call.requireFirebasePrincipal(authVerifier)
            val request = call.receive<CreateNotificationRequest>()
            val notificationId = request.payload["notificationId"]?.toString()?.trim('"').orEmpty()
            notificationStore.upsertNotification(
                uid = request.recipientUid,
                notificationId = notificationId,
                payload = request.payload,
            )
            call.respond(HttpStatusCode.OK)
        }
    }
}
