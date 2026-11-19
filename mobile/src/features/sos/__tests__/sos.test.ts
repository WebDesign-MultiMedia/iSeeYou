import type { SosAlert } from '@/api/types';
import { applySos, sortForDisplay, sosDetail, sosTitle } from '../sos';

const NOW = Date.parse('2026-11-19T12:00:00Z');

const alert = (id: string, overrides: Partial<SosAlert> = {}): SosAlert => ({
  id,
  userId: 'leo',
  userName: 'Leo',
  lat: 40,
  lng: -3,
  accuracy: 10,
  battery: 23,
  active: true,
  createdAt: '2026-11-19T11:58:00Z',
  updatedAt: '2026-11-19T11:58:00Z',
  resolvedAt: null,
  resolvedById: null,
  resolvedByName: null,
  ...overrides,
});

describe('applySos', () => {
  it('adds a new alert first', () => {
    const next = applySos([alert('a')], alert('b'));
    expect(next.map((a) => a.id)).toEqual(['b', 'a']);
  });

  it('updates an open alert in place', () => {
    const next = applySos([alert('a')], alert('a', { lat: 41, updatedAt: '2026-11-19T11:59:00Z' }));
    expect(next).toHaveLength(1);
    expect(next[0].lat).toBe(41);
  });

  it('removes a resolved alert', () => {
    const next = applySos([alert('a'), alert('b')], alert('a', { active: false, updatedAt: '2026-11-19T11:59:00Z' }));
    expect(next.map((a) => a.id)).toEqual(['b']);
  });

  it('ignores stale updates that arrive late', () => {
    const list = [alert('a', { updatedAt: '2026-11-19T11:59:00Z' })];
    expect(applySos(list, alert('a', { active: false, updatedAt: '2026-11-19T11:58:30Z' }))).toBe(list);
  });

  it('ignores a resolution for an alert it never saw', () => {
    const list = [alert('a')];
    expect(applySos(list, alert('zzz', { active: false }))).toBe(list);
  });
});

describe('sortForDisplay', () => {
  it('puts my own SOS first', () => {
    const sorted = sortForDisplay(
      [alert('other', { createdAt: '2026-11-19T11:59:00Z' }), alert('mine', { userId: 'me' })],
      'me',
    );
    expect(sorted[0].id).toBe('mine');
  });
});

describe('wording', () => {
  it('speaks to the sender', () => {
    expect(sosTitle(alert('a', { userId: 'me' }), 'me')).toBe('Your SOS is active');
  });

  it('tells helpers who, when and how much battery', () => {
    expect(sosTitle(alert('a'), 'mom')).toBe('Leo needs help');
    expect(sosDetail(alert('a'), 'mom', NOW)).toBe('Sent 2 min ago · 23% battery');
  });

  it('says when the location is unknown', () => {
    expect(sosDetail(alert('a', { lat: null, lng: null, battery: null }), 'mom', NOW)).toContain('location unknown');
  });
});
