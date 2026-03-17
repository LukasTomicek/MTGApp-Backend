package mtg.app.core.error

open class AppException(message: String) : RuntimeException(message)

class ValidationException(message: String) : AppException(message)
class UnauthorizedException(message: String = "Unauthorized") : AppException(message)
class ForbiddenException(message: String = "Forbidden") : AppException(message)
class ConfigurationException(message: String) : AppException(message)
class ExternalServiceException(message: String) : AppException(message)
