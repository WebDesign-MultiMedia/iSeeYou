import { API_URL } from '@/config';
import { session, type Tokens } from '@/auth/session';
import { isExpiringSoon } from '@/auth/jwt';
import type { AuthResponse, FamilyDetail, FamilySummary, Problem, User } from './types';

/** An HTTP error from the API, carrying the problem+json body when there is one. */
export class ApiError extends Error {
  constructor(readonly status: number, readonly problem: Problem | null) {
    super(problem?.detail ?? problem?.title ?? `Request failed (${status})`);
  }

  /** Per-field validation messages, e.g. { email: "must be a well-formed email address" }. */
  get fieldErrors(): Record<string, string> {
    return this.problem?.errors ?? {};
  }
}

/** Thrown when the API can't be reached at all (server down, wrong Wi-Fi, etc.). */
export class NetworkError extends Error {
  constructor() {
    super(`Can't reach FamilyMap at ${API_URL}. Is the server running and your phone on the same network?`);
  }
}

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

interface RequestOptions {
  method?: Method;
  body?: unknown;
  /** Attach the access token (default true). */
  auth?: boolean;
}

async function send(path: string, { method = 'GET', body, auth = true }: RequestOptions, token?: string) {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (auth && token) headers.Authorization = `Bearer ${token}`;
  try {
    return await fetch(`${API_URL}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new NetworkError();
  }
}

async function parse<T>(res: Response): Promise<T> {
  const text = await res.text();
  const data = text ? JSON.parse(text) : undefined;
  if (!res.ok) throw new ApiError(res.status, (data as Problem) ?? null);
  return data as T;
}

// ---- token refresh ------------------------------------------------------------------------

let refreshing: Promise<Tokens | null> | null = null;

/**
 * Swaps the refresh token for a new pair. Concurrent callers share one in-flight request, so
 * a burst of 401s triggers a single refresh. If the refresh token is rejected, the user is
 * signed out.
 */
function refreshTokens(): Promise<Tokens | null> {
  if (!refreshing) {
    refreshing = (async () => {
      const refreshToken = session.get()?.refreshToken;
      if (!refreshToken) return null;
      try {
        const res = await send('/api/auth/refresh', { method: 'POST', body: { refreshToken }, auth: false });
        if (res.status === 401) {
          await session.clear();
          return null;
        }
        const data = await parse<AuthResponse>(res);
        const tokens = { accessToken: data.accessToken, refreshToken: data.refreshToken };
        await session.set(tokens);
        return tokens;
      } catch (e) {
        if (e instanceof ApiError) await session.clear();
        return null; // network error: keep the session, the next call will retry
      }
    })().finally(() => {
      refreshing = null;
    });
  }
  return refreshing;
}

/** An access token that's valid for at least another minute, refreshing first if needed. */
export async function freshAccessToken(): Promise<string | null> {
  const tokens = session.get();
  if (!tokens) return null;
  if (!isExpiringSoon(tokens.accessToken)) return tokens.accessToken;
  return (await refreshTokens())?.accessToken ?? null;
}

/** Authenticated request with one transparent refresh-and-retry on 401. */
async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  if (options.auth === false) return parse<T>(await send(path, options));

  let res = await send(path, options, (await freshAccessToken()) ?? undefined);
  if (res.status === 401 && session.get()) {
    const tokens = await refreshTokens();
    if (tokens) res = await send(path, options, tokens.accessToken);
  }
  return parse<T>(res);
}

// ---- endpoints ----------------------------------------------------------------------------

async function storeAuth(res: AuthResponse): Promise<User> {
  await session.set({ accessToken: res.accessToken, refreshToken: res.refreshToken });
  return res.user;
}

export const api = {
  register: (name: string, email: string, password: string) =>
    request<AuthResponse>('/api/auth/register', { method: 'POST', body: { name, email, password }, auth: false })
      .then(storeAuth),

  login: (email: string, password: string) =>
    request<AuthResponse>('/api/auth/login', { method: 'POST', body: { email, password }, auth: false })
      .then(storeAuth),

  me: () => request<User>('/api/auth/me'),

  myCircles: () => request<FamilySummary[]>('/api/families'),

  circle: (familyId: string) => request<FamilyDetail>(`/api/families/${familyId}`),

  createCircle: (name: string) => request<FamilySummary>('/api/families', { method: 'POST', body: { name } }),

  joinCircle: (inviteCode: string) =>
    request<FamilySummary>('/api/families/join', { method: 'POST', body: { inviteCode } }),

  leaveCircle: (familyId: string) =>
    request<void>(`/api/families/${familyId}/members/me`, { method: 'DELETE' }),
};
