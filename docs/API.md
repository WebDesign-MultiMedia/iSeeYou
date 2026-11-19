# FamilyMap API

REST + STOMP reference with copy-paste curl walkthroughs. Start the stack first
(`docker compose up --build`, see the [README](../README.md#quick-start)).

## Auth

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

## Family circles

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
| PUT | `/api/families/{id}/members/me/sharing` | Step 5: pause or resume sharing in this circle, `{"enabled": false}`. Returns your member JSON and pushes it live to the circle. Idempotent |

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

## Locations and realtime

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
| Subscribe | `/topic/family/{familyId}/members`: a member paused or resumed sharing (their full member JSON) |
| Subscribe | `/topic/family/{familyId}/events`: `ARRIVED`, `LEFT`, `PLACE_ADDED`, `PLACE_REMOVED` |
| Subscribe | `/topic/family/{familyId}/sos`: an SOS was raised, moved, or resolved (`"active": false`) |
| Publish (optional) | `SEND /app/locations` with the same JSON as the REST body |
| Your errors | subscribe to `/user/queue/errors` |

Every message on the topic looks like this:

```json
{ "userId": "15360958-…", "lat": 40.4168, "lng": -3.7038, "accuracy": 8.0, "battery": 64,
  "recordedAt": "2026-11-19T17:17:06.928680Z" }
```

### Try it: watch a location arrive live

```bash
# Terminal 1: reuse MOM, LEO, CODE and FAMILY_ID from the circles walkthrough above (Leo must have joined)
TOKEN=$MOM node tools/ws-listen.mjs $FAMILY_ID

# Terminal 2: Leo reports a location
curl -s -X POST http://localhost:8080/api/locations -H "Authorization: Bearer $LEO" \
  -H 'Content-Type: application/json' -d '{"lat":40.4168,"lng":-3.7038,"accuracy":8,"battery":64}'
```

Terminal 1 prints the point immediately:

```
12:17:06 PM  user 15360958  40.4168, -3.7038  ±8m  battery 64%  (recorded 2026-11-19T17:17:06.928680Z)
```

## Saved places and push

| Method | Path | |
|---|---|---|
| GET | `/api/families/{id}/places` | The circle's saved places |
| POST | `/api/families/{id}/places` | `{"name","lat","lng","radiusM"}` (50–2000 m, at most 20 per circle). Any member |
| DELETE | `/api/families/{id}/places/{placeId}` | Whoever added it, or an admin; otherwise `403` |
| PUT | `/api/me/push-token` | `{"token":"ExponentPushToken[…]","platform":"ios"\|"android"}`. Registers this phone for arrival alerts |
| DELETE | `/api/me/push-token` | `{"token":"…"}`. The app calls this on sign-out |

An event on `/topic/family/{id}/events`:

```json
{ "type": "ARRIVED", "familyId": "…", "placeId": "…", "placeName": "Home",
  "userId": "…", "userName": "Leo", "at": "2026-11-19T18:02:11Z" }
```

Arrivals and departures are also pushed through Expo's push service to every *other* member
who turned notifications on. Set `PUSH_ENABLED=false` to keep them in-app only.

## SOS and data retention

| Method | Path | |
|---|---|---|
| POST | `/api/sos` | `{"lat?","lng?","accuracy?","battery?"}`. Alerts **every** circle you're in, even ones where sharing is paused. `201` new, `200` if it updated your open SOS. `400` if you're in no circle |
| POST | `/api/sos/{id}/resolve` | The sender ("I'm OK") or anyone in an alerted circle ("handled"). Idempotent. Others get `404` |
| GET | `/api/families/{id}/sos` | Open SOS alerts in the circle |
| DELETE | `/api/me/locations` | Erase all your stored locations now. Returns `{"deleted": n}` and clears your marker live |

Retention runs hourly: location points older than `RETENTION_LOCATIONS` (default `7d`) and SOS
alerts older than `RETENTION_SOS` (`30d`) are deleted in batches of 1,000.
