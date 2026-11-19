import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, ApiError } from '@/api/client';
import type { User } from '@/api/types';
import { session } from './session';

type Status = 'loading' | 'signedOut' | 'signedIn';

interface AuthContextValue {
  status: Status;
  user: User | null;
  signIn: (email: string, password: string) => Promise<void>;
  register: (name: string, email: string, password: string) => Promise<void>;
  signOut: () => Promise<void>;
  /** An invite code from a deep link, kept until the user is signed in and can use it. */
  pendingInvite: string | null;
  setPendingInvite: (code: string | null) => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<Status>('loading');
  const [user, setUser] = useState<User | null>(null);
  const [pendingInvite, setPendingInvite] = useState<string | null>(null);

  // Restore the session on launch.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      const tokens = await session.load();
      if (!tokens) {
        if (!cancelled) setStatus('signedOut');
        return;
      }
      try {
        const me = await api.me();
        if (!cancelled) {
          setUser(me);
          setStatus('signedIn');
        }
      } catch (e) {
        if (e instanceof ApiError && e.status === 401) await session.clear();
        // Offline at launch: stay signed in with what we have; screens show their own errors.
        if (!cancelled) setStatus(session.get() ? 'signedIn' : 'signedOut');
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  // The API client clears the session when a refresh fails; reflect that here.
  useEffect(
    () =>
      session.subscribe((tokens) => {
        if (!tokens) {
          setUser(null);
          setStatus('signedOut');
        }
      }),
    [],
  );

  const signIn = useCallback(async (email: string, password: string) => {
    setUser(await api.login(email.trim(), password));
    setStatus('signedIn');
  }, []);

  const register = useCallback(async (name: string, email: string, password: string) => {
    setUser(await api.register(name.trim(), email.trim(), password));
    setStatus('signedIn');
  }, []);

  const signOut = useCallback(() => session.clear(), []);

  const value = useMemo(
    () => ({ status, user, signIn, register, signOut, pendingInvite, setPendingInvite }),
    [status, user, signIn, register, signOut, pendingInvite],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
