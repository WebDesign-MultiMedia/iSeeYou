import type { LocationPoint, Member } from '@/api/types';
import { applyLocation, applyMemberUpdate, batteryTone, colorFor, initials, lastSeenLabel, statusLine } from '../members';

const NOW = Date.parse('2026-11-19T12:00:00Z');

const point = (userId: string, recordedAt: string, lat = 1): LocationPoint => ({
  userId, lat, lng: 2, accuracy: 10, battery: 50, recordedAt,
});

const member = (userId: string, overrides: Partial<Member> = {}): Member => ({
  userId,
  name: userId.toUpperCase(),
  avatarUrl: null,
  role: 'MEMBER',
  sharingEnabled: true,
  joinedAt: '2026-11-01T00:00:00Z',
  lastLocation: null,
  ...overrides,
});

describe('applyLocation', () => {
  it('sets the first location for a member', () => {
    const roster = [member('leo')];
    const next = applyLocation(roster, point('leo', '2026-11-19T11:00:00Z'));
    expect(next).not.toBe(roster);
    expect(next[0].lastLocation?.recordedAt).toBe('2026-11-19T11:00:00Z');
  });

  it('replaces an older location with a newer one', () => {
    const roster = [member('leo', { lastLocation: point('leo', '2026-11-19T11:00:00Z', 1) })];
    const next = applyLocation(roster, point('leo', '2026-11-19T11:05:00Z', 9));
    expect(next[0].lastLocation?.lat).toBe(9);
  });

  it('ignores an out-of-order (older) point and returns the same array', () => {
    const roster = [member('leo', { lastLocation: point('leo', '2026-11-19T11:05:00Z') })];
    expect(applyLocation(roster, point('leo', '2026-11-19T11:00:00Z'))).toBe(roster);
  });

  it('ignores points for unknown users and for paused members', () => {
    const roster = [member('leo', { sharingEnabled: false })];
    expect(applyLocation(roster, point('stranger', '2026-11-19T11:00:00Z'))).toBe(roster);
    expect(applyLocation(roster, point('leo', '2026-11-19T11:00:00Z'))).toBe(roster);
  });

  it('does not touch other members', () => {
    const mom = member('mom');
    const next = applyLocation([mom, member('leo')], point('leo', '2026-11-19T11:00:00Z'));
    expect(next[0]).toBe(mom);
  });
});

describe('lastSeenLabel', () => {
  it.each([
    ['2026-11-19T11:59:40Z', 'Just now'],
    ['2026-11-19T11:55:00Z', '5 min ago'],
    ['2026-11-19T09:00:00Z', '3 h ago'],
    ['2026-11-18T10:00:00Z', 'Yesterday'],
  ])('%s -> %s', (iso, label) => {
    expect(lastSeenLabel(iso, NOW)).toBe(label);
  });

  it('treats slightly-future timestamps (clock skew) as just now', () => {
    expect(lastSeenLabel('2026-11-19T12:00:30Z', NOW)).toBe('Just now');
  });
});

describe('statusLine', () => {
  it('describes paused, missing and known locations', () => {
    expect(statusLine(member('a', { sharingEnabled: false }), NOW)).toBe('Location sharing paused');
    expect(statusLine(member('a'), NOW)).toBe('No location yet');
    expect(statusLine(member('a', { lastLocation: point('a', '2026-11-19T11:55:00Z') }), NOW))
      .toBe('5 min ago · 50% battery');
  });
});

describe('small helpers', () => {
  it('batteryTone', () => {
    expect(batteryTone(null)).toBe('unknown');
    expect(batteryTone(8)).toBe('critical');
    expect(batteryTone(20)).toBe('low');
    expect(batteryTone(64)).toBe('ok');
  });

  it('initials', () => {
    expect(initials('Ana María López')).toBe('AL');
    expect(initials('leo')).toBe('L');
    expect(initials('   ')).toBe('?');
  });

  it('colorFor is stable per user', () => {
    expect(colorFor('15360958-d236')).toBe(colorFor('15360958-d236'));
    expect(colorFor('x')).toMatch(/^#[0-9a-f]{6}$/);
  });
});

describe('applyMemberUpdate', () => {
  it('replaces the member, so pausing removes their location', () => {
    const roster = [member('mom'), member('leo', { lastLocation: point('leo', '2026-11-19T11:00:00Z') })];
    const next = applyMemberUpdate(roster, member('leo', { sharingEnabled: false, lastLocation: null }));
    expect(next).not.toBe(roster);
    expect(next[1].sharingEnabled).toBe(false);
    expect(next[1].lastLocation).toBeNull();
    expect(next[0]).toBe(roster[0]);
  });

  it('ignores members not in the roster', () => {
    const roster = [member('mom')];
    expect(applyMemberUpdate(roster, member('eve'))).toBe(roster);
  });
});
