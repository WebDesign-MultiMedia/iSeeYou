# FamilyMap

A **consent-based** family location-sharing app (a lightweight Life360).
A family admin creates a circle, members join by invite code and share their live location,
and everyone in the circle sees each other on a map.

> **Transparency by design.** Every member opts in, always sees a visible "sharing on"
> indicator, and can pause sharing at any time. FamilyMap does not support hidden or
> covert tracking. For a minor, a guardian sets it up on a device they manage, and the
> indicator stays visible to the child.

| Layer    | Tech |
|----------|------|
| Mobile   | React Native (Expo + dev client / EAS), react-native-maps, expo-location *(step 4+)* |
| Backend  | Java 21, Spring Boot 3.5 (Web, Security, Data JPA, Validation, Actuator) |
| Auth     | Stateless JWT (access + refresh), BCrypt |
| Realtime | WebSocket / STOMP *(step 3)* |
| Database | MySQL 8 + Flyway |
| Push     | Firebase Cloud Messaging *(step 6)* |
| Deploy   | Docker + docker-compose |

## Project status

- [x] **Step 1:** Backend scaffold + JWT auth
- [ ] Step 2: Family circles
- [ ] Step 3: Location ingestion + realtime
- [ ] Step 4: Mobile app: auth, join, live map
- [ ] Step 5: Background location, battery, pause
- [ ] Step 6: Saved places, geofencing, push
- [ ] Step 7: SOS + history cleanup
- [ ] Step 8: Portfolio polish

## Repository layout

```
.
├── docker-compose.yml        # MySQL 8 + API
├── .env.example              # copy to .env (secrets live here, never committed)
└── backend/
    ├── Dockerfile            # multi-stage build -> slim JRE, non-root
    ├── pom.xml
    └── src/main/java/com/familymap/
        ├── config/           # Spring Security, CORS, beans
        ├── controller/       # REST endpoints (thin: validate, delegate)
        ├── service/          # business rules + authorization
        ├── repository/       # Spring Data JPA
        ├── domain/           # JPA entities
        ├── dto/              # request/response records
        ├── security/         # JWT issue/verify, auth filter, 401 handler
        └── exception/        # RFC 9457 problem+json error mapping
```

## Running locally

### Prerequisites
- Docker Desktop (or Docker Engine with the Compose plugin)
- *Optional, to run tests or the API outside Docker:* JDK 21. The Maven wrapper (`./mvnw`) downloads Maven for you.

### 1. Configure secrets

```bash
cp .env.example .env
# Generate a JWT signing key and write it into .env:
sed -i '' "s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -base64 48)|" .env   # macOS
# sed -i  "s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -base64 48)|" .env   # Linux
```

| Variable | Required | Purpose |
|----------|----------|---------|
| `JWT_SECRET` | **yes** | Base64 HMAC key, at least 32 bytes. The app refuses to start without it. |
| `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD` | **yes** | MySQL credentials (set your own values) |
| `DB_NAME`, `DB_USER` | no | Default `familymap` |
| `JWT_ACCESS_TTL` / `JWT_REFRESH_TTL` | no | Default `15m` / `7d` |
| `CORS_ALLOWED_ORIGINS` | no | Default `*` (dev only) |

### 2. Start the stack

```bash
docker compose up --build
# wait for: "Started FamilyMapApplication"
curl http://localhost:8080/actuator/health      # {"status":"UP",...}
```

## API (step 1)

All errors are returned as `application/problem+json` ([RFC 9457](https://www.rfc-editor.org/rfc/rfc9457)).

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/auth/register` | none | Create an account, returns tokens (`201`) |
| POST | `/api/auth/login` | none | Exchange email + password for tokens |
| POST | `/api/auth/refresh` | none | Exchange a refresh token for a new pair |
| GET  | `/api/auth/me` | Bearer | The current user |

### curl walkthrough

```bash
# Register
curl -s -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"name":"Ana","email":"ana@example.com","password":"s3cret-pass"}'

# Login, then keep the access token in a shell variable (uses jq: brew install jq)
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"ana@example.com","password":"s3cret-pass"}' | jq -r .accessToken)

# Who am I?
curl -s http://localhost:8080/api/auth/me -H "Authorization: Bearer $TOKEN"

# Without a token -> 401 problem+json
curl -i http://localhost:8080/api/auth/me
```

Example login response:

```json
{
  "accessToken": "eyJhbGciOiJIUzM4NCJ9...",
  "refreshToken": "eyJhbGciOiJIUzM4NCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": { "id": "3f1c…", "name": "Ana", "email": "ana@example.com", "avatarUrl": null, "createdAt": "2026-11-16T09:00:00Z" }
}
```

## Tests

```bash
cd backend
./mvnw test
```

- **Unit:** `JwtServiceTest` (expiry, tampering, wrong key, token-type confusion) and `AuthServiceTest` (hashing, email normalization, duplicate email, generic login errors).
- **Integration:** `AuthControllerIntegrationTest` runs the full Spring context, security chain, and real Flyway migrations against H2 in MySQL mode.

## Design notes (step 1)

- **Stateless JWT, two token types.** Access tokens are short-lived (15 min). Refresh tokens
  (7 days) carry `typ=REFRESH` and are rejected where an access token is expected.
  *Tradeoff:* stateless refresh tokens can't be revoked before they expire. Storing
  refresh tokens server-side with rotation is a planned hardening step.
- **No account enumeration.** Login returns the same message for an unknown email and for a
  wrong password, and still runs a BCrypt comparison when the email doesn't exist, so the
  two cases take about the same time.
- **Flyway owns the schema.** Hibernate runs with `ddl-auto=validate`, so a mismatch between
  entity and schema fails at startup instead of silently changing the database.
- **UUIDs as `BINARY(16)`.** Uses less space and indexes better than `CHAR(36)`.
- **Fail fast on config.** The app refuses to start if `JWT_SECRET` is missing or shorter than 256 bits.
