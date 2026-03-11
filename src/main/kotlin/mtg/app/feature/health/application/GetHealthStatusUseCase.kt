package mtg.app.feature.health.application

import mtg.app.feature.health.domain.HealthStatus

class GetHealthStatusUseCase {
    operator fun invoke(): HealthStatus {
        return HealthStatus(
            status = "ok",
            service = "mtg-backend",
        )
    }
}
