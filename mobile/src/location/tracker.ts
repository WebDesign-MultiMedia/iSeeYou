import { isRunningInExpoGo } from 'expo';
import * as Battery from 'expo-battery';
import * as Location from 'expo-location';
import * as TaskManager from 'expo-task-manager';
import { PROFILES, powerMode, type PowerMode, type Precision } from './policy';
import { readPower, reportFixes, resetReporter } from './reporter';
import { sharingStatus, type TrackingMode } from './status';

/**
 * Starts and stops GPS tracking in one of two modes:
 *
 * - **background**: `startLocationUpdatesAsync` + a TaskManager task. Keeps sharing when the
 *   app is closed. Needs "Always" permission and a real build (not Expo Go). On Android it
 *   runs as a foreground service with a permanent notification, so the user can always see
 *   that their location is being shared.
 * - **foreground**: `watchPositionAsync`. Shares only while the app is open. Works everywhere,
 *   including Expo Go.
 *
 * Both re-tune themselves when the power mode changes (see `policy.ts`).
 */

export const LOCATION_TASK = 'familymap.location-updates';

const ACCURACY: Record<Precision, Location.Accuracy> = {
  high: Location.Accuracy.High,
  balanced: Location.Accuracy.Balanced,
  low: Location.Accuracy.Low,
};

let watcher: Location.LocationSubscription | null = null;
let batteryListeners: { remove(): void }[] = [];

/** Can this build track in the background at all (before asking for permission)? */
export async function backgroundSupported(): Promise<boolean> {
  if (isRunningInExpoGo()) return false; // Expo Go: Android has no TaskManager, iOS can't run it
  try {
    return await TaskManager.isAvailableAsync();
  } catch {
    return false;
  }
}

async function backgroundAllowed(): Promise<boolean> {
  if (!(await backgroundSupported())) return false;
  return (await Location.getBackgroundPermissionsAsync()).granted;
}

function backgroundOptions(mode: PowerMode): Location.LocationTaskOptions {
  const p = PROFILES[mode];
  return {
    accuracy: ACCURACY[p.precision],
    distanceInterval: p.distanceM,
    timeInterval: p.intervalMs,
    // iOS: batch updates when the screen is off; let the OS pause when we're clearly not moving.
    deferredUpdatesInterval: p.intervalMs,
    pausesUpdatesAutomatically: mode !== 'normal',
    activityType: Location.ActivityType.Other,
    // iOS shows the blue status-bar pill while we track in the background: transparency by design.
    showsBackgroundLocationIndicator: true,
    foregroundService: {
      notificationTitle: 'Sharing your location',
      notificationBody: 'Your family circles can see where you are. Open FamilyMap to pause.',
      killServiceOnDestroy: false,
    },
  };
}

async function startForeground(mode: PowerMode) {
  watcher?.remove();
  const p = PROFILES[mode];
  watcher = await Location.watchPositionAsync(
    { accuracy: ACCURACY[p.precision], distanceInterval: p.distanceM, timeInterval: p.intervalMs },
    (loc) => void onFixes([loc]),
  );
}

async function startBackground(mode: PowerMode) {
  // Calling start again with new options re-tunes a running task; no need to stop first.
  await Location.startLocationUpdatesAsync(LOCATION_TASK, backgroundOptions(mode));
}

/** Re-tune when the battery crosses a threshold, so we don't wait for the next fix to notice. */
function listenToBattery() {
  stopBatteryListeners();
  const check = async () => void retune(powerMode(await readPower()));
  try {
    batteryListeners = [
      Battery.addBatteryLevelListener(check),
      Battery.addBatteryStateListener(check),
      Battery.addLowPowerModeListener(check),
    ];
  } catch {
    batteryListeners = []; // no battery API on this platform
  }
}

function stopBatteryListeners() {
  batteryListeners.forEach((l) => l.remove());
  batteryListeners = [];
}

async function retune(mode: PowerMode) {
  const status = sharingStatus.get();
  if (status.mode === 'off' || mode === status.power) return;
  sharingStatus.update({ power: mode });
  if (status.mode === 'background') await startBackground(mode);
  else await startForeground(mode);
}

/** Called with every batch of fixes from either mode. */
export async function onFixes(locations: Location.LocationObject[]) {
  const { result, power } = await reportFixes(locations);
  if (result === 'paused') {
    await stopTracking({ keepPausedFlag: true });
    return;
  }
  await retune(power);
}

/**
 * Starts tracking, in the background if this build and the user's permission allow it,
 * otherwise in the foreground. Assumes foreground permission was already granted.
 */
export async function startTracking(): Promise<TrackingMode> {
  const mode = powerMode(await readPower());
  const background = await backgroundAllowed();

  if (background) {
    watcher?.remove();
    watcher = null;
    await startBackground(mode);
  } else {
    if (await isBackgroundTaskRunning()) await Location.stopLocationUpdatesAsync(LOCATION_TASK);
    await startForeground(mode);
  }
  listenToBattery();
  const tracking: TrackingMode = background ? 'background' : 'foreground';
  sharingStatus.update({ mode: tracking, power: mode, pausedEverywhere: false });
  return tracking;
}

export async function stopTracking({ keepPausedFlag = false } = {}) {
  watcher?.remove();
  watcher = null;
  stopBatteryListeners();
  if (await isBackgroundTaskRunning()) await Location.stopLocationUpdatesAsync(LOCATION_TASK);
  sharingStatus.update({ mode: 'off', pausedEverywhere: keepPausedFlag && sharingStatus.get().pausedEverywhere });
  if (!keepPausedFlag) resetReporter();
}

async function isBackgroundTaskRunning(): Promise<boolean> {
  try {
    return await Location.hasStartedLocationUpdatesAsync(LOCATION_TASK);
  } catch {
    return false; // TaskManager unavailable (Expo Go on Android)
  }
}
