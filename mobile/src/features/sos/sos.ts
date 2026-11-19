import type { SosAlert } from '@/api/types';
import { lastSeenLabel } from '@/features/map/members';

/** Pure SOS helpers, kept out of React so they're unit tested. */

/** How long the button must be held. Long enough to rule out a pocket press, short in a crisis. */
export const HOLD_TO_SEND_MS = 2_000;

/**
 * Merges a live SOS update into the list of open alerts: new ones go first, updates replace
 * in place, resolved ones are removed. Stale updates (older `updatedAt`) are ignored.
 */
export function applySos(alerts: SosAlert[], update: SosAlert): SosAlert[] {
  const index = alerts.findIndex((a) => a.id === update.id);
  if (index !== -1 && Date.parse(alerts[index].updatedAt) > Date.parse(update.updatedAt)) return alerts;
  if (!update.active) return index === -1 ? alerts : alerts.filter((a) => a.id !== update.id);
  if (index === -1) return [update, ...alerts];
  const next = alerts.slice();
  next[index] = update;
  return next;
}

/** Mine first (so "I'm OK" is always reachable), then newest first. */
export function sortForDisplay(alerts: SosAlert[], myUserId: string | undefined): SosAlert[] {
  return [...alerts].sort((a, b) => {
    const mine = Number(b.userId === myUserId) - Number(a.userId === myUserId);
    return mine !== 0 ? mine : Date.parse(b.createdAt) - Date.parse(a.createdAt);
  });
}

export function sosTitle(alert: SosAlert, myUserId: string | undefined): string {
  return alert.userId === myUserId ? 'Your SOS is active' : `${alert.userName} needs help`;
}

export function sosDetail(alert: SosAlert, myUserId: string | undefined, now: number = Date.now()): string {
  if (alert.userId === myUserId) {
    return alert.lat == null
      ? 'Your circles were alerted. Your location couldn’t be found.'
      : 'Your circles can see where you are until you mark yourself OK.';
  }
  const parts = [`Sent ${lastSeenLabel(alert.createdAt, now).toLowerCase()}`];
  if (alert.battery != null) parts.push(`${alert.battery}% battery`);
  if (alert.lat == null) parts.push('location unknown');
  return parts.join(' · ');
}

export const hasLocation = (a: SosAlert): a is SosAlert & { lat: number; lng: number } =>
  a.lat != null && a.lng != null;
