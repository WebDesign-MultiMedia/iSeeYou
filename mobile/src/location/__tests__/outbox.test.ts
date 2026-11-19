import { enqueue, pruneExpired, type PendingPoint } from '../outbox';

const p = (recordedAt: string, lat = 1): PendingPoint => ({ lat, lng: 2, accuracy: 10, battery: 50, recordedAt });

describe('enqueue', () => {
  it('appends in order', () => {
    const q = enqueue(enqueue([], p('2026-11-19T10:00:00Z', 1)), p('2026-11-19T10:01:00Z', 2));
    expect(q.map((x) => x.lat)).toEqual([1, 2]);
  });

  it('drops the oldest when full', () => {
    let q: PendingPoint[] = [];
    for (let i = 0; i < 5; i++) q = enqueue(q, p('2026-11-19T10:00:00Z', i), 3);
    expect(q.map((x) => x.lat)).toEqual([2, 3, 4]);
  });

  it('does not mutate the input', () => {
    const q: PendingPoint[] = [];
    enqueue(q, p('2026-11-19T10:00:00Z'));
    expect(q).toHaveLength(0);
  });
});

describe('pruneExpired', () => {
  it('drops points older than the max age', () => {
    const now = Date.parse('2026-11-19T12:00:00Z');
    const q = [p('2026-11-19T11:00:00Z', 1), p('2026-11-19T09:00:00Z', 2)];
    expect(pruneExpired(q, now, 2 * 60 * 60_000).map((x) => x.lat)).toEqual([1]);
  });
});
