# Backend Architecture

This backend follows a feature-first clean architecture split on top of Ktor + PostgreSQL.

## Layers

Each feature is split into:

- `api`: Ktor routes, request/response DTOs, domain mapping.
- `application`: use cases with validation and orchestration.
- `domain`: business models and repository contracts.
- `infrastructure`: concrete adapters (PostgreSQL repositories).

Shared cross-cutting code lives in `core`:

- `core/plugins`: Ktor plugin setup.
- `core/error`: shared exceptions and error response format.
- `core/db`: datasource + SQL migrations bootstrap.

App bootstrap lives in `app`:

- `app/AppModule.kt`: assembles plugins and routes.
- `app/AppDependencies.kt`: dependency wiring.

## Practices

- Keep route handlers thin; call use cases only.
- Put validation in use cases, not routes.
- Keep domain free of Ktor/framework types.
- Use repository interfaces in domain/application; keep DB details in infrastructure.
- Return consistent JSON errors through `StatusPages`.

## Current features

- `health`: basic service health endpoint.
- `offers`: create/list/delete BUY/SELL offers.
- `market`: card summaries and seller summaries (with `excludeUserId`).
- `matches`: BUY/SELL counterpart match summaries for a specific user.
- `users`: save/load profile nickname and nickname batch lookup.
