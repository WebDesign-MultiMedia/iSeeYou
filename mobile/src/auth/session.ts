import * as SecureStore from 'expo-secure-store';

/**
 * The signed-in session's tokens: in memory for fast access, persisted in the OS keychain /
 * keystore (expo-secure-store) so the user stays signed in across launches. Tokens never go
 * to AsyncStorage, which is unencrypted.
 *
 * A tiny observable: the API client writes to it (login, refresh, 401), and AuthProvider
 * subscribes to re-render when the user signs in or out.
 */
export interface Tokens {
  accessToken: string;
  refreshToken: string;
}

const KEY = 'familymap.session.v1';

type Listener = (tokens: Tokens | null) => void;

let current: Tokens | null = null;
const listeners = new Set<Listener>();

export const session = {
  get(): Tokens | null {
    return current;
  },

  /** Reads persisted tokens once at startup. */
  async load(): Promise<Tokens | null> {
    try {
      const raw = await SecureStore.getItemAsync(KEY);
      current = raw ? (JSON.parse(raw) as Tokens) : null;
    } catch {
      current = null; // corrupt or unreadable keychain entry: start signed out
    }
    return current;
  },

  async set(tokens: Tokens): Promise<void> {
    current = tokens;
    await SecureStore.setItemAsync(KEY, JSON.stringify(tokens));
    listeners.forEach((l) => l(current));
  },

  async clear(): Promise<void> {
    current = null;
    await SecureStore.deleteItemAsync(KEY);
    listeners.forEach((l) => l(null));
  },

  subscribe(listener: Listener): () => void {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
};
