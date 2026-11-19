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
| Mobile   | React Native 0.88 / Expo SDK 58, Expo Router, react-native-maps, @stomp/stompjs, expo-secure-store |
| Backend  | Java 21, Spring Boot 3.5 (Web, Security, Data JPA, Validation, Actuator) |
| Auth     | Stateless JWT (access + refresh), BCrypt |
| Realtime | WebSocket / STOMP (Spring simple broker) |
| Database | MySQL 8 + Flyway |
| Push     | Firebase Cloud Messaging *(step 6)* |
| Deploy   | Docker + docker-compose |

## Project status

- [x] **Step 1:** Backend scaffold + JWT auth
- [x] **Step 2:** Family circles
- [x] **Step 3:** Location ingestion + realtime
- [x] **Step 4:** Mobile app: auth, join, live map
- [ ] Step 5: Background location, battery, pause
- [ ] Step 6: Saved places, geofencing, push
- [ ] Step 7: SOS + history cleanup
- [ ] Step 8: Portfolio polish

## Repository layout

```
.
├── docker-compose.yml        # MySQL 8 + API
├── .env.example              # copy to .env (secrets live here, never committed)
├── tools/
│   ├── ws-listen.mjs         # zero-dependency STOMP listener for manual testing (Node 22+)
│   └── simulate-walk.mjs     # demo: a fake family member walking a loop
├── mobile/                   # Expo app (see "Mobile app" below)
│   └── src/
│       ├── app/              # Expo Router screens: (auth)/, (app)/, invite/[code]
│       ├── api/              # typed API client + token refresh
│       ├── auth/             # secure session storage, AuthProvider
│       ├── realtime/         # useLiveCircle: REST + STOMP live roster
│       ├── features/map/     # markers, roster, pure helpers (unit tested)
│       └── components/       # Button, TextField, Avatar, FormScreen
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
        ├── realtime/         # STOMP auth interceptor, broadcaster, /app message handlers
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

## API (step 3): locations + realtime

### REST

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/locations` | Report a point `{"lat","lng","accuracy?","battery?","recordedAt?"}`. `201` if shared, `200` with `"shared":false` if sharing is paused in every circle (the point is **discarded**) |

`GET /api/families/{id}` and `/members` now include each member's `lastLocation`. It is
`null` for a member who has paused sharing in that circle.

Validation: `lat` must be within ±90, `lng` within ±180, `battery` 0–100, `accuracy` ≥ 0 meters.
`recordedAt` defaults to the server time and is rejected if it's more than 2 minutes in the
future or older than the 7-day retention window.

### WebSocket (STOMP)

| | |
|---|---|
| Endpoint | `ws://localhost:8080/ws` (plain WebSocket, no SockJS) |
| Auth | STOMP `CONNECT` header `Authorization: Bearer <accessToken>` |
| Subscribe | `/topic/family/{familyId}/locations` (members only) |
| Publish (optional) | `SEND /app/locations` with the same JSON as the REST body |
| Your errors | subscribe to `/user/queue/errors` |

Every message on the topic looks like this:

```json
{ "userId": "15360958-…", "lat": 40.4168, "lng": -3.7038, "accuracy": 8.0, "battery": 64,
  "recordedAt": "2026-11-19T17:17:06.928680Z" }
```

### Try it: watch a location arrive live

```bash
# Terminal 1: reuse MOM, LEO, CODE and FAMILY_ID from the step 2 walkthrough (Leo must have joined)
TOKEN=$MOM node tools/ws-listen.mjs $FAMILY_ID

# Terminal 2: Leo reports a location
curl -s -X POST http://localhost:8080/api/locations -H "Authorization: Bearer $LEO" \
  -H 'Content-Type: application/json' -d '{"lat":40.4168,"lng":-3.7038,"accuracy":8,"battery":64}'
```

Terminal 1 prints the point immediately:

```
12:17:06 PM  user 15360958  40.4168, -3.7038  ±8m  battery 64%  (recorded 2026-11-19T17:17:06.928680Z)
```

## Mobile app (step 4)

Map-first: your family on a full-screen map, with a panel listing each member's last-seen
time, battery and sharing status. Markers move as soon as a new point arrives over the
WebSocket.

### Run it on your phone (Expo Go)

You need the **Expo Go** app on your phone, with the phone on the **same Wi-Fi** as this computer.

```bash
docker compose up -d                 # the API must be running
cd mobile
npm install
npx expo start                       # scan the QR code with your phone's camera (iOS) or Expo Go (Android)
```

There's nothing to configure. In development the app calls the API on the machine running
`expo start` (port 8080). To use another server, set `EXPO_PUBLIC_API_URL` in `mobile/.env`
(see `mobile/.env.example`).

> **macOS firewall:** if the app says it can't reach FamilyMap, allow incoming connections
> for Docker in *System Settings → Network → Firewall → Options*, then check from another
> device: `curl http://<your-mac-ip>:8080/actuator/health`.

### Watch markers move

1. In the app, register and create a circle. The panel shows its invite code.
2. On your computer, start a fake family member who joins and walks a loop:
   ```bash
   node tools/simulate-walk.mjs --code <INVITE-CODE> --lat <your-lat> --lng <your-lng>
   ```
3. Their marker appears and moves every 2 seconds, and the battery slowly drains.

Invite links use `familymap://invite/<code>`. The **Invite** button shares a link and the code.
If the person isn't signed in yet, the app remembers the code until they sign in.

### Checks

```bash
cd mobile
npm run typecheck    # tsc --noEmit
npm test             # jest-expo unit tests
npx expo-doctor      # dependency / config health
```

## Tests

```bash
cd backend
./mvnw test
```

- **Unit:** `JwtServiceTest` (expiry, tampering, wrong key, token-type confusion) and `AuthServiceTest` (hashing, email normalization, duplicate email, generic login errors).
- **Unit (step 2):** `FamilyServiceTest` (creator becomes admin, code collisions, joining again, outsiders get 404, admin handover, deleting the circle when the last member leaves) and `InviteCodeGeneratorTest`.
- **Unit (step 3):** `LocationServiceTest` (sharing per circle, discarding points when sharing is paused everywhere, timestamp window, choosing between points with the same timestamp).
- **Realtime:** `LocationRealtimeIntegrationTest` starts the real server on a random port and connects a real STOMP client: a posted point reaches a subscribed member live, `SEND /app/locations` works, bad payloads go to the sender's error queue, outsiders and bad tokens are rejected, and paused members are neither stored nor shown.
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

## Design notes (step 3)

- **Consent is enforced on the server.** A point is shared only with circles where the member
  has sharing on. Paused everywhere means it is discarded, not stored. A paused member's
  last known location is hidden too, not just new points.
- **One authorization rule for REST and WebSocket.** The STOMP interceptor checks `SUBSCRIBE`
  with the same `FamilyAccessService.requireMember` that the REST endpoints use. Clients may only
  `SEND` to `/app/**`, never straight to a `/topic`, so nobody can publish a fake location
  for someone else.
- **JWT in the STOMP CONNECT frame,** because browser and React Native WebSocket APIs can't set
  headers on the handshake. Putting the token in the URL would leak it into access logs.
- **Broadcast after commit** (`@TransactionalEventListener(AFTER_COMMIT)`), so subscribers never
  see a point that was rolled back.
- **Points belong to the user, not the circle.** Someone in two circles reports once, and each
  circle where sharing is on gets it.
- **Latest location without N+1:** one roster query plus one "latest per user" query, served by
  the `(user_id, recorded_at)` index.
- **Late uploads keep their real time.** A phone that was offline can upload points afterwards
  with their original timestamps, so clients ignore a point older than the one they're showing.
- **Scaling note.** The in-memory simple broker works for one API instance. To run several,
  switch to a broker relay (RabbitMQ or ActiveMQ via `enableStompBrokerRelay`) so all
  instances share topics.
- **Known limitation.** A member who leaves a circle keeps an existing subscription until they
  reconnect. *Planned:* close their sessions when they leave.

## Design notes (step 4)

- **Tokens in the keychain/keystore** (`expo-secure-store`), never in AsyncStorage. The access
  token is refreshed shortly before it expires. If a burst of requests gets 401, they share
  one refresh call, and a rejected refresh signs the user out.
- **The auth gate uses `Stack.Protected`.** Screens for signed-in users can't be reached while
  signed out, even through deep links. The splash screen stays up until the session is
  restored, so the sign-in screen never flashes.
- **Realtime that survives mobile networks.** `useLiveCircle` loads over REST, then subscribes
  over STOMP. It reloads after every reconnect so no point is missed, ignores out-of-order
  points, sends 10 s heartbeats, and disconnects while the app is in the background. A fresh
  token is fetched before each reconnect.
- **Honest data.** A *Live / Reconnecting* badge, "last seen" times, and an accuracy halo for
  imprecise fixes keep people from trusting a position more than they should.
- **Accessibility.** Labeled inputs, errors announced to screen readers, 48 dp touch targets,
  markers and rows labeled with name and status, light and dark themes, and colors with
  ≥ 4.5:1 contrast.
- **Expo Go first.** Everything in this step runs in Expo Go. Background location (step 5)
  needs a development build, which comes then.
