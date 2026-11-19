import * as Battery from 'expo-battery';
import type { LocationObject } from 'expo-location';
import { api, ApiError, NetworkError } from '@/api/client';
import { session } from '@/auth/session';
import { enqueue, pruneExpired, type PendingPoint } from './outbox';
import { PROFILES, powerMode, shouldSend, toPercent, type Fix, type PowerInfo, type PowerMode } from './policy';
import { sharingStatus } from './status';

/**
 * Turns raw GPS fixes into uploads. Used by both the foreground watcher and the background
 * task, so the throttling, battery reading and offline queue behave the same in both.
 */

let lastSent: Fix | null = null;
let outbox: PendingPoint[] = [];
let flushing = false;

export type ReportResult = 'sent' | 'skipped' | 'queued' | 'paused';

/** Battery level, charging state and Low Power Mode, tolerating platforms that report nothing. */
export async function readPower(): Promise<PowerInfo> {
  try {
    const p = await Battery.getPowerStateAsync();
    return {
      level: toPercent(p.batteryLevel),
      charging: p.batteryState === Battery.BatteryState.CHARGING || p.batteryState === Battery.BatteryState.FULL,
      lowPowerMode: p.lowPowerMode,
    };
  } catch {
    return { level: null, charging: false, lowPowerMode: false };
  }
}

/**
 * Handles the newest fix in a batch (the OS may deliver several at once). Also returns the
 * current power mode, so the caller can re-tune tracking when the battery crosses a threshold.
 */
export async function reportFixes(locations: LocationObject[]): Promise<{ result: ReportResult; power: PowerMode }> {
  const latest = locations.reduce<LocationObject | null>(
    (a, b) => (a == null || b.timestamp > a.timestamp ? b : a),
    null,
  );
  const power = await readPower();
  const mode = powerMode(power);
  if (!latest) return { result: 'skipped', power: mode };

  // A cold-started background task has no session in memory yet.
  if (!session.get()) await session.load();
  if (!session.get()) return { result: 'skipped', power: mode };

  const fix: Fix = {
    lat: latest.coords.latitude,
    lng: latest.coords.longitude,
    accuracy: latest.coords.accuracy ?? null,
    at: latest.timestamp,
  };
  if (!shouldSend(lastSent, fix, PROFILES[mode])) return { result: 'skipped', power: mode };
  lastSent = fix;

  outbox = enqueue(outbox, {
    lat: fix.lat,
    lng: fix.lng,
    accuracy: fix.accuracy == null ? null : Math.min(Math.max(fix.accuracy, 0), 100_000),
    battery: power.level,
    recordedAt: new Date(fix.at).toISOString(),
  });
  return { result: await flush(), power: mode };
}

/** Uploads queued points oldest first; stops at the first network failure and keeps the rest. */
export async function flush(): Promise<ReportResult> {
  if (flushing) return 'queued';
  flushing = true;
  let result: ReportResult = 'queued';
  try {
    outbox = pruneExpired(outbox, Date.now());
    while (outbox.length > 0) {
      const [next, ...rest] = outbox;
      try {
        const res = await api.reportLocation(next);
        outbox = rest;
        if (!res.shared) {
          // Paused in every circle: the server discarded it, and so will it discard the rest.
          outbox = [];
          sharingStatus.update({ pausedEverywhere: true });
          result = 'paused';
          break;
        }
        sharingStatus.update({ lastSentAt: next.recordedAt, pausedEverywhere: false });
        result = 'sent';
      } catch (e) {
        if (e instanceof NetworkError) break; // offline: keep the queue for next time
        if (e instanceof ApiError && e.status >= 400 && e.status < 500 && e.status !== 401) {
          outbox = rest; // the server will never accept this point (e.g. bad timestamp): drop it
          continue;
        }
        break; // 5xx or signed out: try again later
      }
    }
  } finally {
    flushing = false;
    sharingStatus.update({ pending: outbox.length });
  }
  return result;
}

/** Forget everything (sign-out, or the user turned sharing off on this device). */
export function resetReporter() {
  lastSent = null;
  outbox = [];
  sharingStatus.update({ pending: 0 });
}
