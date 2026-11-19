/**
 * Reads a JWT's `exp` claim without verifying it. The server verifies; the client only wants
 * to know whether to refresh before using the token.
 */
export function jwtExpiresAt(token: string): number | null {
  try {
    const payload = token.split('.')[1];
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(payload.length / 4) * 4, '='));
    const exp = JSON.parse(json).exp;
    return typeof exp === 'number' ? exp * 1000 : null;
  } catch {
    return null;
  }
}

/** True if the token expires within `skewMs` (default 60 s), or can't be read. */
export function isExpiringSoon(token: string, now = Date.now(), skewMs = 60_000): boolean {
  const exp = jwtExpiresAt(token);
  return exp === null || exp - skewMs <= now;
}
