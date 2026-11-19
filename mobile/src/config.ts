import Constants from 'expo-constants';

/**
 * Where the FamilyMap API lives.
 *
 * 1. `EXPO_PUBLIC_API_URL` if set (e.g. a deployed API).
 * 2. Otherwise, in development: the machine running the Expo dev server, port 8080. Your phone
 *    already reaches that machine to load the JS bundle, so the API is reachable on the same
 *    Wi-Fi with zero config.
 * 3. Fallback: localhost (iOS simulator / web).
 */
function resolveApiUrl(): string {
  const fromEnv = process.env.EXPO_PUBLIC_API_URL;
  if (fromEnv) return fromEnv.replace(/\/+$/, '');

  const devHost = Constants.expoConfig?.hostUri?.split(':')[0];
  if (devHost) return `http://${devHost}:8080`;

  return 'http://localhost:8080';
}

export const API_URL = resolveApiUrl();

/** STOMP endpoint: same host, ws:// or wss:// to match http:// or https://. */
export const WS_URL = `${API_URL.replace(/^http/, 'ws')}/ws`;

/** Deep link that opens the join screen with the code filled in. */
export const inviteLink = (code: string) => `familymap://invite/${encodeURIComponent(code)}`;
