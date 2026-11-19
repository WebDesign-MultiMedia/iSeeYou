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
- [x] **Step 2:** Family circles
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

## API (step 2): family circles

All endpoints require `Authorization: Bearer <accessToken>`. Anyone who isn't a member of a
circle gets **404** for it, the same response as a circle that doesn't exist.

| Method | Path | Description |
|--------|------|-------------|
| POST   | `/api/families` | Create a circle `{"name":"…"}`; you become `ADMIN` (`201`) |
| POST   | `/api/families/join` | Join with `{"inviteCode":"K7QM-3XPA"}` (case, spaces and dash ignored; joining again is harmless) |
| GET    | `/api/families` | Circles you belong to, with your role and the member count |
| GET    | `/api/families/{id}` | A circle with its members |
| GET    | `/api/families/{id}/members` | Just the members |
| DELETE | `/api/families/{id}/members/me` | Leave the circle (`204`) |

### curl walkthrough: two users, one circle

```bash
API=http://localhost:8080
reg() { curl -s -X POST $API/api/auth/register -H 'Content-Type: application/json' \
  -d "{\"name\":\"$1\",\"email\":\"$2\",\"password\":\"s3cret-pass\"}" | jq -r .accessToken; }

MOM=$(reg Mom mom@example.com)
LEO=$(reg Leo leo@example.com)

# Mom creates the circle and gets the invite code
CIRCLE=$(curl -s -X POST $API/api/families -H "Authorization: Bearer $MOM" \
  -H 'Content-Type: application/json' -d '{"name":"The Smiths"}')
FAMILY_ID=$(echo "$CIRCLE" | jq -r .id); CODE=$(echo "$CIRCLE" | jq -r .inviteCode)

# Leo joins with the code
curl -s -X POST $API/api/families/join -H "Authorization: Bearer $LEO" \
  -H 'Content-Type: application/json' -d "{\"inviteCode\":\"$CODE\"}"

# Both now see the same roster
curl -s $API/api/families/$FAMILY_ID -H "Authorization: Bearer $LEO" | jq

# Leo leaves
curl -i -X DELETE $API/api/families/$FAMILY_ID/members/me -H "Authorization: Bearer $LEO"
```

## Tests

```bash
cd backend
./mvnw test
```

- **Unit:** `JwtServiceTest` (expiry, tampering, wrong key, token-type confusion) and `AuthServiceTest` (hashing, email normalization, duplicate email, generic login errors).
- **Unit (step 2):** `FamilyServiceTest` (creator becomes admin, code collisions, joining again, outsiders get 404, admin handover, deleting the circle when the last member leaves) and `InviteCodeGeneratorTest`.
- **Integration:** `AuthControllerIntegrationTest` and `FamilyControllerIntegrationTest` (two or three real users per test) run the full Spring context, security chain, and real Flyway migrations against H2 in MySQL mode.

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

## Design notes (step 2)

- **Authorization in the service layer.** `FamilyAccessService.requireMember` is the single
  check every circle-scoped operation calls first, and later steps (locations, places, SOS)
  reuse it. A controller can't skip it.
- **404 instead of 403 for outsiders.** A stranger can't tell whether a circle id exists.
- **Invite codes** look like `K7QM-3XPA`. They use 8 characters from `SecureRandom`, from an
  alphabet without look-alikes (no `0/O`, `1/I`): about 1.1 trillion combinations, easy to
  read aloud. *Planned hardening:* rate-limit `/join` and let admins rotate the code.
- **A circle always has an admin.** If the last admin leaves, the longest-standing member is
  promoted. If the last member leaves, the circle is deleted.
- **Data minimization.** Rosters show name, avatar, role and sharing status. Emails are
  never exposed to other members.
- **No N+1 queries.** "My circles" loads the memberships and their families with a single
  join, then fetches all member counts in one grouped query.
