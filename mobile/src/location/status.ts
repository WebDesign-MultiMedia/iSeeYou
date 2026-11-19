import type { PowerMode } from './policy';

/**
 * Live state of location sharing on this device. A tiny observable (like `session`), because
 * it's written from outside React: the background task runs even when no screen is mounted.
 */

export type TrackingMode = 'off' | 'foreground' | 'background';

export interface SharingStatus {
  mode: TrackingMode;
  power: PowerMode;
  /** ISO time of the last point the server accepted. */
  lastSentAt: string | null;
  /** Points waiting for a connection. */
  pending: number;
  /** The server said sharing is paused in every circle, so tracking stopped to save battery. */
  pausedEverywhere: boolean;
}

type Listener = (s: SharingStatus) => void;

const initial: SharingStatus = { mode: 'off', power: 'normal', lastSentAt: null, pending: 0, pausedEverywhere: false };
let current = initial;
const listeners = new Set<Listener>();

export const sharingStatus = {
  get: (): SharingStatus => current,

  update(patch: Partial<SharingStatus>) {
    const next = { ...current, ...patch };
    const changed = (Object.keys(patch) as (keyof SharingStatus)[]).some((k) => next[k] !== current[k]);
    if (!changed) return;
    current = next;
    listeners.forEach((l) => l(current));
  },

  reset() {
    sharingStatus.update(initial);
  },

  subscribe(listener: Listener): () => void {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
};
