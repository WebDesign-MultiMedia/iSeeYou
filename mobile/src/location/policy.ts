/**
 * Battery-aware tracking rules. Pure functions, no native modules, so they're unit tested.
 *
 * The trade-off: a family wants a fresh dot, but a tracker that drains the phone gets turned
 * off. So precision and frequency step down as the battery does, and go back up on charge.
 */

export type PowerMode = 'normal' | 'saver' | 'critical';

export interface PowerInfo {
  /** 0-100, or null when the OS doesn't report it (simulators, some Android devices). */
  level: number | null;
  charging: boolean;
  lowPowerMode: boolean;
}

/** GPS precision we ask the OS for. Mapped to expo-location's Accuracy in the tracker. */
export type Precision = 'high' | 'balanced' | 'low';

export interface TrackingProfile {
  precision: Precision;
  /** Minimum movement (meters) before the OS wakes us with a new fix. */
  distanceM: number;
  /** Minimum time between fixes (Android; iOS uses distance only). */
  intervalMs: number;
  /** Even when standing still, send a point this often so family sees "just now", not "2 h ago". */
  heartbeatMs: number;
}

const MINUTE = 60_000;

export const PROFILES: Record<PowerMode, TrackingProfile> = {
  normal: { precision: 'high', distanceM: 25, intervalMs: 15_000, heartbeatMs: 5 * MINUTE },
  saver: { precision: 'balanced', distanceM: 100, intervalMs: MINUTE, heartbeatMs: 15 * MINUTE },
  critical: { precision: 'low', distanceM: 300, intervalMs: 5 * MINUTE, heartbeatMs: 30 * MINUTE },
};

export const POWER_MODE_LABEL: Record<PowerMode, string> = {
  normal: 'Normal',
  saver: 'Battery saver',
  critical: 'Low battery',
};

/** Charging always gets full precision; otherwise step down at 20% (or Low Power Mode) and 10%. */
export function powerMode({ level, charging, lowPowerMode }: PowerInfo): PowerMode {
  if (charging) return 'normal';
  if (level != null && level <= 10) return 'critical';
  if (lowPowerMode || (level != null && level <= 20)) return 'saver';
  return 'normal';
}

/** expo-battery reports 0..1, or -1 when unknown. */
export function toPercent(level: number): number | null {
  if (!Number.isFinite(level) || level < 0) return null;
  return Math.round(Math.min(1, level) * 100);
}

export interface Fix {
  lat: number;
  lng: number;
  /** meters, null if unknown */
  accuracy: number | null;
  /** epoch ms */
  at: number;
}

/** A fix this rough is worse than no update at all, unless we have nothing yet. */
export const MAX_USEFUL_ACCURACY_M = 1_000;

/**
 * Should this fix be uploaded, given the last one we sent?
 *
 * The OS already filters by distance, but it can still deliver bursts (several fixes at once
 * after waking, or jitter while standing still), so we de-duplicate here to save data and battery.
 */
export function shouldSend(last: Fix | null, next: Fix, profile: TrackingProfile): boolean {
  if (!last) return true;
  if (next.at <= last.at) return false; // out of order or duplicate
  if (next.accuracy != null && next.accuracy > MAX_USEFUL_ACCURACY_M) return false;
  if (next.at - last.at >= profile.heartbeatMs) return true;
  return distanceMeters(last, next) >= profile.distanceM;
}

/** Great-circle distance (haversine). Plenty accurate at family-map scale. */
export function distanceMeters(a: Pick<Fix, 'lat' | 'lng'>, b: Pick<Fix, 'lat' | 'lng'>): number {
  const R = 6_371_000;
  const rad = (d: number) => (d * Math.PI) / 180;
  const dLat = rad(b.lat - a.lat);
  const dLng = rad(b.lng - a.lng);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
}
