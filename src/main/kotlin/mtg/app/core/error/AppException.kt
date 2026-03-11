package mtg.app.core.error

open class AppException(message: String) : RuntimeException(message)

class ValidationException(message: String) : AppException(message)
