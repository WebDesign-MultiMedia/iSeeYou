import * as Location from 'expo-location';
import { api } from '@/api/client';
import type { SosAlert } from '@/api/types';
import { readPower } from '@/location/reporter';

/** Never let a slow GPS delay an SOS by more than this. */
const FIX_TIMEOUT_MS = 6_000;

/**
 * Sends an SOS with the best position available right now: a fresh fix if one comes quickly,
 * else the last known one, else none at all (the SOS still goes out).
 */
export async function sendSos(): Promise<SosAlert> {
  const [position, power] = await Promise.all([bestPosition(), readPower()]);
  return api.raiseSos({
    ...(position
      ? { lat: position.coords.latitude, lng: position.coords.longitude, accuracy: position.coords.accuracy ?? null }
      : {}),
    battery: power.level,
  });
}

async function bestPosition(): Promise<Location.LocationObject | null> {
  try {
    if (!(await Location.getForegroundPermissionsAsync()).granted) return null;
    const fresh = Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.High });
    const timeout = new Promise<null>((resolve) => setTimeout(() => resolve(null), FIX_TIMEOUT_MS));
    return (await Promise.race([fresh, timeout])) ?? (await Location.getLastKnownPositionAsync());
  } catch {
    return null;
  }
}
