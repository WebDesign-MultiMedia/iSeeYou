/**
 * Points that couldn't be uploaded (no signal, server down) wait here and go out, oldest
 * first, with their real timestamps once the connection is back. The server accepts late
 * points up to 7 days old and the map ignores anything older than what it already shows,
 * so a late upload never moves a marker backwards.
 *
 * Kept in memory only: a location trail is sensitive, so it isn't written to disk. If the OS
 * kills the app, the queue is lost, and the next fix simply becomes the latest point.
 */

export interface PendingPoint {
  lat: number;
  lng: number;
  accuracy: number | null;
  battery: number | null;
  /** ISO-8601 */
  recordedAt: string;
}

/** Enough for hours offline at the saver rate, small enough to flush in a few seconds. */
export const MAX_PENDING = 200;
/** A bit under the server's 7-day limit, so a point never expires while in flight. */
export const MAX_PENDING_AGE_MS = 6 * 24 * 60 * 60_000;

/** Adds a point; when full, the oldest points are dropped first (the newest matter most). */
export function enqueue(queue: readonly PendingPoint[], point: PendingPoint, max = MAX_PENDING): PendingPoint[] {
  const next = [...queue, point];
  return next.length > max ? next.slice(next.length - max) : next;
}

/** Drops points the server would reject as too old. */
export function pruneExpired(queue: readonly PendingPoint[], now: number, maxAgeMs = MAX_PENDING_AGE_MS): PendingPoint[] {
  return queue.filter((p) => now - Date.parse(p.recordedAt) < maxAgeMs);
}
