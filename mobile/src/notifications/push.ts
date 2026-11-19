import { isRunningInExpoGo } from 'expo';
import Constants from 'expo-constants';
import * as Device from 'expo-device';
import * as Notifications from 'expo-notifications';
import { AndroidImportance } from 'expo-notifications';
import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';
import { api } from '@/api/client';

/**
 * Arrival alerts as push notifications. Opt-in: nothing is registered until the user turns
 * alerts on from the circle menu.
 *
 * Push needs (a) a real device, (b) an EAS project id (`npx eas-cli@latest init`), and (c) on
 * Android, a development or store build: Expo Go on Android dropped remote push in SDK 53.
 * When any of these is missing, the in-app live banners still work.
 */

const TOKEN_KEY = 'familymap.pushToken.v1';
/** Must match PushNotifier.ANDROID_CHANNEL / SOS_CHANNEL on the server. */
const ANDROID_CHANNEL = 'arrivals';
const SOS_CHANNEL = 'sos';

export type PushResult =
  | { ok: true }
  | { ok: false; reason: 'denied' | 'simulator' | 'expo-go-android' | 'no-project' | 'error'; message: string };

/** In the foreground the in-app banner already shows the event, so don't double up. */
export function configureForegroundHandling() {
  Notifications.setNotificationHandler({
    handleNotification: async () => ({
      shouldShowBanner: false,
      shouldShowList: true,
      shouldPlaySound: false,
      shouldSetBadge: false,
    }),
  });
}

function projectId(): string | undefined {
  return Constants.expoConfig?.extra?.eas?.projectId ?? Constants.easConfig?.projectId;
}

export async function pushEnabled(): Promise<boolean> {
  return (await SecureStore.getItemAsync(TOKEN_KEY)) != null;
}

export async function enablePush(): Promise<PushResult> {
  if (!Device.isDevice) {
    return { ok: false, reason: 'simulator', message: 'Push notifications need a real phone.' };
  }
  if (Platform.OS === 'android' && isRunningInExpoGo()) {
    return {
      ok: false,
      reason: 'expo-go-android',
      message: 'Expo Go on Android can’t receive push notifications. You’ll still see alerts while the app is open.',
    };
  }
  const id = projectId();
  if (!id) {
    return {
      ok: false,
      reason: 'no-project',
      message: 'Push isn’t set up for this build yet (it needs an EAS project). Alerts show while the app is open.',
    };
  }

  if (Platform.OS === 'android') {
    await Notifications.setNotificationChannelAsync(ANDROID_CHANNEL, {
      name: 'Arrivals and departures',
      description: 'When someone in your circle arrives at or leaves a saved place',
      importance: AndroidImportance.HIGH,
    });
    // Separate so people can quiet arrivals but keep SOS loud.
    await Notifications.setNotificationChannelAsync(SOS_CHANNEL, {
      name: 'SOS',
      description: 'When someone in your circle asks for help',
      importance: AndroidImportance.MAX,
      vibrationPattern: [0, 500, 250, 500, 250, 500],
      bypassDnd: true,
    });
  }

  const current = await Notifications.getPermissionsAsync();
  const granted = current.granted || (await Notifications.requestPermissionsAsync()).granted;
  if (!granted) {
    return { ok: false, reason: 'denied', message: 'Notifications are turned off for FamilyMap in Settings.' };
  }

  try {
    const { data: token } = await Notifications.getExpoPushTokenAsync({ projectId: id });
    await api.registerPushToken(token, Platform.OS === 'ios' ? 'ios' : 'android');
    await SecureStore.setItemAsync(TOKEN_KEY, token);
    return { ok: true };
  } catch {
    return { ok: false, reason: 'error', message: 'Couldn’t turn on notifications. Please try again.' };
  }
}

/** Stops alerts on this device. Also called on sign-out, so a shared phone stops getting them. */
export async function disablePush(): Promise<void> {
  const token = await SecureStore.getItemAsync(TOKEN_KEY);
  if (!token) return;
  try {
    await api.unregisterPushToken(token);
  } catch {
    // Offline or already signed out: the server drops dead tokens when Expo reports them.
  }
  await SecureStore.deleteItemAsync(TOKEN_KEY);
}

/** The circle a tapped notification is about, if any. */
export function familyIdFrom(response: Notifications.NotificationResponse | null | undefined): string | null {
  const data = response?.notification.request.content.data as { familyId?: unknown } | undefined;
  return typeof data?.familyId === 'string' ? data.familyId : null;
}
