import type { LocationPoint, Member } from '@/api/types';

/**
 * Pure helpers behind the live map. They live outside React so they're trivial to unit test.
 */

/**
 * Applies a realtime location update to the roster, returning a new array if anything changed
 * and the same array if not, so React skips re-rendering.
 *
 * Ignored when:
 * - the user isn't in this roster (e.g. joined after we loaded it; a refetch picks them up),
 * - the member has paused sharing (defense in depth: the server shouldn't send these anyway),
 * - the point is older than what we already show (late uploads can arrive out of order).
 */
export function applyLocation(members: Member[], point: LocationPoint): Member[] {
  const index = members.findIndex((m) => m.userId === point.userId);
  if (index === -1) return members;

  const member = members[index];
  if (!member.sharingEnabled) return members;
  if (member.lastLocation && Date.parse(member.lastLocation.recordedAt) >= Date.parse(point.recordedAt)) {
    return members;
  }

  const next = members.slice();
  next[index] = { ...member, lastLocation: point };
  return next;
}

/**
 * Applies a roster update (someone paused or resumed sharing). The server sends the member's
 * full current state, so it simply replaces ours: a paused member arrives with no location,
 * which removes their marker. Unknown members are ignored; a refetch picks them up.
 */
export function applyMemberUpdate(members: Member[], updated: Member): Member[] {
  const index = members.findIndex((m) => m.userId === updated.userId);
  if (index === -1) return members;
  const next = members.slice();
  next[index] = updated;
  return next;
}

/** "Just now", "5 min ago", "3 h ago", "Yesterday", or a short date. */
export function lastSeenLabel(recordedAt: string, now: number = Date.now()): string {
  const diffMs = Math.max(0, now - Date.parse(recordedAt));
  const minutes = Math.floor(diffMs / 60_000);
  if (minutes < 1) return 'Just now';
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} h ago`;
  if (hours < 48) return 'Yesterday';
  return new Date(recordedAt).toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}

export type BatteryTone = 'ok' | 'low' | 'critical' | 'unknown';

export function batteryTone(level: number | null | undefined): BatteryTone {
  if (level == null) return 'unknown';
  if (level <= 10) return 'critical';
  if (level <= 20) return 'low';
  return 'ok';
}

/** Up to two initials: "Ana María López" -> "AL", "leo" -> "L". */
export function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '?';
  const first = parts[0][0];
  const last = parts.length > 1 ? parts[parts.length - 1][0] : '';
  return (first + last).toUpperCase();
}

/** Accessible, distinguishable marker colors (all pass 4.5:1 contrast with white text). */
const PALETTE = ['#1a73e8', '#c5221f', '#188038', '#b06000', '#8430ce', '#007b83', '#c2185b', '#5f6368'];

/** A stable color per user, so someone keeps their color across sessions. */
export function colorFor(userId: string): string {
  let hash = 0;
  for (let i = 0; i < userId.length; i++) hash = (hash * 31 + userId.charCodeAt(i)) | 0;
  return PALETTE[Math.abs(hash) % PALETTE.length];
}

/** Human description of a member's status for screen readers and the roster. */
export function statusLine(member: Member, now: number = Date.now()): string {
  if (!member.sharingEnabled) return 'Location sharing paused';
  const loc = member.lastLocation;
  if (!loc) return 'No location yet';
  const battery = loc.battery != null ? ` · ${loc.battery}% battery` : '';
  return `${lastSeenLabel(loc.recordedAt, now)}${battery}`;
}
