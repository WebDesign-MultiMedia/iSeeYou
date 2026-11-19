import * as Location from 'expo-location';
import * as SecureStore from 'expo-secure-store';
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { AppState } from 'react-native';
import { useAuth } from '@/auth/AuthProvider';
import { sharingStatus, type SharingStatus } from './status';
import { backgroundSupported, startTracking, stopTracking } from './tracker';

/**
 * Device-level location sharing: the user's choice to share from this phone, the OS
 * permission, and the tracker. Per-circle pause lives on the server (see `api.setSharing`).
 *
 * Nothing is tracked until the user has read the explanation screen and said yes, and the
 * choice is remembered per sign-in: signing out stops tracking and forgets it.
 */

/** `ask`: we may show the OS prompt. `blocked`: denied for good, only Settings can fix it. */
export type Permission = 'unknown' | 'ask' | 'blocked' | 'granted';

interface LocationSharingValue extends SharingStatus {
  /** The user said yes on the explanation screen (and hasn't turned it off since). */
  consented: boolean;
  permission: Permission;
  /** This build can share while the app is closed (false in Expo Go). */
  canUseBackground: boolean;
  /** Shows the OS prompt(s) and starts tracking. Resolves false if the user declined. */
  enable: (options: { background: boolean }) => Promise<boolean>;
  /** Stops tracking on this device entirely. */
  disable: () => Promise<void>;
  /** Restarts tracking after resuming a circle that was the only one paused. */
  resume: () => Promise<void>;
}

const CONSENT_KEY = 'familymap.locationConsent.v1';
const Ctx = createContext<LocationSharingValue | null>(null);

function toPermission(p: Location.LocationPermissionResponse): Permission {
  if (p.granted) return 'granted';
  return p.canAskAgain ? 'ask' : 'blocked';
}

export function LocationSharingProvider({ children }: { children: ReactNode }) {
  const { status: auth } = useAuth();
  const [status, setStatus] = useState(sharingStatus.get());
  const [consented, setConsented] = useState(false);
  const [permission, setPermission] = useState<Permission>('unknown');
  const [canUseBackground, setCanUseBackground] = useState(false);

  useEffect(() => sharingStatus.subscribe(setStatus), []);

  // Signed in: restore the previous choice and pick up where we left off. Signed out: stop.
  useEffect(() => {
    if (auth === 'loading') return;
    let cancelled = false;
    (async () => {
      if (auth === 'signedOut') {
        await stopTracking();
        await SecureStore.deleteItemAsync(CONSENT_KEY);
        if (!cancelled) setConsented(false);
        return;
      }
      const [saved, perm, bg] = await Promise.all([
        SecureStore.getItemAsync(CONSENT_KEY),
        Location.getForegroundPermissionsAsync(),
        backgroundSupported(),
      ]);
      if (cancelled) return;
      setConsented(saved === 'on');
      setPermission(toPermission(perm));
      setCanUseBackground(bg);
      if (saved === 'on' && perm.granted) await startTracking();
    })().catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [auth]);

  // Back from Settings: the user may have granted or revoked access there.
  useEffect(() => {
    const sub = AppState.addEventListener('change', async (state) => {
      if (state !== 'active' || auth !== 'signedIn') return;
      const perm = toPermission(await Location.getForegroundPermissionsAsync());
      setPermission(perm);
      const consent = (await SecureStore.getItemAsync(CONSENT_KEY)) === 'on';
      const s = sharingStatus.get();
      if (perm !== 'granted' && s.mode !== 'off') await stopTracking();
      else if (perm === 'granted' && consent && s.mode === 'off' && !s.pausedEverywhere) await startTracking();
    });
    return () => sub.remove();
  }, [auth]);

  const enable = useCallback(async ({ background }: { background: boolean }) => {
    const fg = await Location.requestForegroundPermissionsAsync();
    setPermission(toPermission(fg));
    if (!fg.granted) return false;
    // "Always" is optional: if declined, we share while the app is open.
    if (background && (await backgroundSupported())) await Location.requestBackgroundPermissionsAsync();
    await SecureStore.setItemAsync(CONSENT_KEY, 'on');
    setConsented(true);
    await startTracking();
    return true;
  }, []);

  const disable = useCallback(async () => {
    await SecureStore.setItemAsync(CONSENT_KEY, 'off');
    setConsented(false);
    await stopTracking();
  }, []);

  const resume = useCallback(async () => {
    if (!consented || permission !== 'granted') return;
    if (sharingStatus.get().mode === 'off') await startTracking();
  }, [consented, permission]);

  const value = useMemo(
    () => ({ ...status, consented, permission, canUseBackground, enable, disable, resume }),
    [status, consented, permission, canUseBackground, enable, disable, resume],
  );
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useLocationSharing(): LocationSharingValue {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error('useLocationSharing must be used inside <LocationSharingProvider>');
  return ctx;
}
