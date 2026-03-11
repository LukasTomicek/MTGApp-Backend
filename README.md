# mtg-backend

Feature-first clean architecture backend (Ktor + PostgreSQL).

## Run locally

1. Ensure PostgreSQL is running.
2. Run backend:

```bash
./gradlew run
```

## Endpoints

### Health
- `GET /health`

### Offers
- `GET /v1/offers?type=SELL&cardId=c1&userId=u1`
- `POST /v1/offers`
- `DELETE /v1/offers/{id}`

POST payload example:

```json
{
  "userId": "u1",
  "cardId": "c1",
  "cardName": "Lightning Bolt",
  "type": "SELL",
  "price": 2.5
}
```

### Market
- `GET /v1/market/cards?query=bolt&excludeUserId=u1`
- `GET /v1/market/sellers?cardId=c1&excludeUserId=u1`
- `GET /v1/market/sellers?cardName=Lightning%20Bolt`

### Matches
- `GET /v1/matches?userId=u1`

Returns possible BUY/SELL counterpart matches for the given user grouped by card + counterpart user.

### User profile
- `PUT /v1/users/profile`
- `GET /v1/users/profile/{userId}`

## Architecture

See [ARCHITECTURE.md](ARCHITECTURE.md).
