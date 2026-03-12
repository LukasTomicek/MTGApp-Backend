package mtg.app.core.auth

import io.ktor.server.application.ApplicationCall

fun ApplicationCall.requireFirebasePrincipal(verifier: FirebaseAuthVerifier): FirebasePrincipal {
    return verifier.verifyBearerToken(request.headers["Authorization"])
}
