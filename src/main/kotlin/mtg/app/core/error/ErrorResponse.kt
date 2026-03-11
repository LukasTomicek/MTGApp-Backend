package mtg.app.core.error

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val message: String,
)
