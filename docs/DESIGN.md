# Design notes

The decisions behind FamilyMap and the tradeoffs they accept, by area. The short version
is in the README's [security & consent design](../README.md#security--consent-design).

## Authentication

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

## Circles and authorization

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

## Locations and realtime

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

## Mobile app: sessions, realtime, accessibility

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
- **Expo Go first.** Sign-in, circles and the live map run in Expo Go. Background location
  needs a development build (`eas build --profile development`).

## Location sharing and battery

- **Consent before the prompt.** Nothing is tracked until the user has read a plain-language
  screen: who sees it, pause, battery, 7-day retention. The choice is remembered per sign-in,
  and signing out stops tracking and forgets it.
- **Pause is per circle and total.** A paused member shows up as "paused" with no location at
  all, not even their last one. The change is pushed live on `/topic/family/{id}/members`.
  When the server reports sharing is paused in every circle, the phone stops the GPS entirely
  instead of sending points that would be thrown away.
- **Battery-aware.** High precision with 25 m / 15 s updates normally. Balanced precision with
  100 m / 1 min at ≤ 20% or in Low Power Mode. Coarse with 300 m / 5 min at ≤ 10%. Full
  precision whenever charging. A heartbeat (5–30 min) keeps "last seen" fresh while standing
  still, and jitter and duplicate fixes are filtered before upload.
- **Offline-tolerant, not hoarding.** Failed uploads wait in an in-memory queue (max 200,
  under 6 days old) and go out oldest first with their real timestamps. The trail is never
  written to disk.
- **Never invisible.** Background tracking runs as an Android foreground service with a
  permanent notification and shows the blue location pill on iOS.
- **One code path.** The foreground watcher and the background task both go through the
  same `reporter`, so throttling, battery handling and the offline queue behave the same in
  both modes.

## Places, geofencing and push

- **Geofencing on the server, not the phone.** Every stored point is checked against the
  places of the circles it was shared with. It costs no extra battery or permission, behaves
  the same on iOS, Android and Expo Go, and can only use locations the member agreed to share
  with that circle.
- **No flapping.** You're inside within the radius, but only outside again past the radius
  plus max(25 m, 20%). Fixes less precise than the place itself decide nothing. Late offline
  uploads never rewind a newer state.
- **No surprise announcements.** The first time someone is seen near a place sets a baseline
  silently, so turning on sharing at home doesn't announce "arrived at Home". Pausing or
  leaving a circle forgets presence, so resuming doesn't reveal where you went while paused.
- **Push can't break location uploads.** Notifications go out after commit and off the request
  thread, are sent in batches of 100, failures are only logged, and tokens Expo reports as
  `DeviceNotRegistered` are deleted.
- **Opt-in alerts, cleaned up on sign-out.** The push token is registered only when the user
  asks, and is unregistered before the session is cleared, so a shared phone doesn't keep
  getting someone else's alerts.

## SOS and data retention

- **SOS overrides a pause, on purpose and in the open.** Pausing stops routine tracking.
  Holding SOS for two seconds is a deliberate, in-the-moment request to be found, so it goes
  to every circle you're in, with your position. The button's accessibility hint says so.
  Routine tracking stays paused.
- **Hard to trigger by accident, easy on purpose.** Hold for 2 s with visual and haptic
  feedback, and letting go cancels. A confirm dialog replaces hold for screen readers. An
  SOS never waits more than 6 s for GPS: it falls back to the last known position, or goes
  out without one ("location unknown, try calling").
- **One alarm per SOS.** Pressing again while one is open updates its position instead of
  re-alarming everyone. Push goes out on raise and resolve only, on a separate max-importance
  Android channel, so people can mute arrivals and keep SOS loud.
- **Retention is a feature.** Location points live for 7 days, SOS alerts for 30. The purge
  runs hourly in 1,000-row transactions so it never holds long locks on the busiest table.
  "Delete my location history" erases everything immediately and clears the marker live.
