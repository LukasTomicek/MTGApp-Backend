# mtg-backend

Feature-first clean architecture backend (Ktor + PostgreSQL).

## Run locally

1. Ensure PostgreSQL is running.
2. Run backend:

```bash
./gradlew run
```

## OpenAPI

The current API contract lives in:

- `openapi.yaml`

Recommended workflow:

1. change backend route / request / response
2. update `openapi.yaml` in the same commit
3. validate it in Swagger Editor
4. only then merge or deploy

Production server documented in the spec:

- `https://mtgapp-backend.onrender.com`

## Testing

Run backend tests:

```bash
./gradlew test
```

Current first test coverage focuses on critical data flows:

- `SyncOffersUseCase`
- `LoadMatchesUseCase`

## Architecture

See [ARCHITECTURE.md](ARCHITECTURE.md).
