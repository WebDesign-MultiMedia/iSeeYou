import { isExpiringSoon, jwtExpiresAt } from '../jwt';

/** Builds an unsigned JWT-shaped string with the given payload (base64url, no padding). */
const tokenWith = (payload: object) => {
  const b64url = (o: object) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${b64url({ alg: 'HS384' })}.${b64url(payload)}.signature`;
};

describe('jwt helpers', () => {
  const now = Date.parse('2026-11-19T12:00:00Z');

  it('reads exp in milliseconds', () => {
    expect(jwtExpiresAt(tokenWith({ exp: now / 1000 + 900 }))).toBe(now + 900_000);
  });

  it('flags tokens expiring within a minute', () => {
    expect(isExpiringSoon(tokenWith({ exp: now / 1000 + 30 }), now)).toBe(true);
    expect(isExpiringSoon(tokenWith({ exp: now / 1000 + 600 }), now)).toBe(false);
  });

  it('treats unreadable tokens as expiring, so the client refreshes', () => {
    expect(jwtExpiresAt('garbage')).toBeNull();
    expect(isExpiringSoon('garbage', now)).toBe(true);
  });
});
