# Deploying FamilyMap

Two independent pieces:

- **API**: one Docker image (`backend/Dockerfile`) plus a MySQL 8 database.
- **App**: built and signed in the cloud with **EAS Build**, then installed on phones.

## Before you deploy the API

| Variable | Value in production |
|---|---|
| `JWT_SECRET` | A fresh `openssl rand -base64 48`. Never reuse the local one |
| `DB_HOST` `DB_PORT` `DB_NAME` `DB_USER` `DB_PASSWORD` | Your database, **or** `DB_URL` with a full JDBC URL (needed for TLS, e.g. `jdbc:mysql://host:3306/familymap?sslMode=REQUIRED&serverTimezone=UTC`) |
| `CORS_ALLOWED_ORIGINS` | The mobile app doesn't need CORS. Set your web origin if you add one, never `*` |
| `PUSH_ENABLED` | `true` |
| `EXPO_ACCESS_TOKEN` | Only if *Enhanced push security* is on in your Expo project |
| `PORT` | Set by the host. The API listens on it automatically |

Health check path: `/actuator/health`. Flyway migrates the database on startup.

> **Run exactly one instance.** Realtime uses Spring's in-memory STOMP broker and the retention
> job runs in-process. Both are correct for one instance. Scaling out needs a broker relay
> (RabbitMQ) and a scheduler lock (ShedLock). See [DESIGN.md](DESIGN.md#locations-and-realtime).
> Also turn off scale-to-zero: sleeping drops every WebSocket and skips the hourly purge.

### Railway (simplest: API + MySQL in one project)

1. **New Project → Deploy from GitHub repo**, pick this repo. In the service settings set
   **Root Directory** to `backend`. Railway finds the Dockerfile.
2. **+ New → Database → MySQL** in the same project.
3. On the API service, **Variables** (reference variables keep the DB on Railway's private network):
   ```
   DB_HOST=${{MySQL.MYSQLHOST}}
   DB_PORT=${{MySQL.MYSQLPORT}}
   DB_NAME=${{MySQL.MYSQLDATABASE}}
   DB_USER=${{MySQL.MYSQLUSER}}
   DB_PASSWORD=${{MySQL.MYSQLPASSWORD}}
   JWT_SECRET=<openssl rand -base64 48>
   ```
4. **Settings → Networking → Generate Domain**, and set the health check path to `/actuator/health`.
5. Check it: `curl https://<your-app>.up.railway.app/actuator/health` returns `{"status":"UP"}`.

### Fly.io

Fly has no managed MySQL, so point it at an external one (Railway, Aiven, or your own) with `DB_URL`.

```bash
cd backend
fly launch --no-deploy          # detects the Dockerfile; pick a region near your database
fly secrets set JWT_SECRET="$(openssl rand -base64 48)" \
  DB_URL="jdbc:mysql://<host>:3306/familymap?sslMode=REQUIRED&serverTimezone=UTC" \
  DB_USER=<user> DB_PASSWORD=<password>
```

In the generated `fly.toml`, set `internal_port = 8080`, `auto_stop_machines = "off"` and
`min_machines_running = 1`, add an HTTP check on `/actuator/health`, then `fly deploy`.

### Render

**New → Web Service → this repo**, Runtime **Docker**, Root Directory `backend`, health check
path `/actuator/health`. Render's managed databases are Postgres only, so use an external MySQL
via `DB_URL` as on Fly. Pick a paid instance type: free instances sleep.

## Building the app with EAS

Expo Go is fine for trying the app, but two features need a real build: **background location**
and, on Android, **push notifications**. EAS builds both in the cloud. You need a free Expo
account. iOS device builds also need a paid Apple Developer account.

### One-time setup

```bash
cd mobile
npx eas-cli@latest login
npx eas-cli@latest init        # creates the Expo project and writes its projectId into app.json
```

The `projectId` is what lets the app get a push token, so commit the change to `app.json`.

### Development build (replaces Expo Go, keeps hot reload)

```bash
npx eas-cli@latest build --profile development --platform android   # or ios
# iOS only, before the first build: register your phone
npx eas-cli@latest device:create
```

Install it from the link EAS prints, then run `npx expo start` and open the project from the
FamilyMap dev app instead of Expo Go. The API address is still taken from the dev server, as before.

### Preview / production builds

Release builds have no dev server to find the API through, so set its public **HTTPS** address
in `mobile/eas.json` (`EXPO_PUBLIC_API_URL` under `preview` and `production`). iOS and Android
both block plain `http://` in release builds.

```bash
npx eas-cli@latest build --profile preview --platform android    # an APK you can sideload
npx eas-cli@latest build --profile production --platform all    # store builds
npx eas-cli@latest submit --platform all                          # upload to the stores
```

### Push credentials

- **iOS:** EAS creates the APNs key during the first build if you let it manage credentials.
- **Android:** create a Firebase project, add an Android app with package `com.familymap.app`,
  generate a service-account key for **FCM HTTP v1**, and upload it with
  `npx eas-cli@latest credentials` → Android → *Push Notifications: FCM V1*.

The API sends every notification through Expo's push service, which delivers through APNs
and FCM, so the server never holds Apple or Google credentials.

### Store review notes

Background location gets extra scrutiny. FamilyMap's answers are already in the product:
the consent screen before any OS prompt, the permanent Android notification and iOS location
pill, per-circle pause, and 7-day retention. The permission strings are in `app.json`.
