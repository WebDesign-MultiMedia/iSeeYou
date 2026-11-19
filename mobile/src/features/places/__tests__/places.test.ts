import type { CircleEvent, Place } from '@/api/types';
import { canRemovePlace, changesPlaces, eventText, formatRadius, validatePlaceName } from '../places';

const place = (createdBy: string): Place => ({
  id: 'p1', familyId: 'f1', name: 'Home', lat: 0, lng: 0, radiusM: 100, createdBy,
  createdAt: '2026-11-19T12:00:00Z',
});

const event = (type: CircleEvent['type'], userId = 'leo'): CircleEvent => ({
  type, familyId: 'f1', placeId: 'p1', placeName: 'Home', userId, userName: 'Leo', at: '2026-11-19T12:00:00Z',
});

describe('validatePlaceName', () => {
  it('requires a name', () => {
    expect(validatePlaceName('   ')).not.toBeNull();
  });

  it('accepts a normal name', () => {
    expect(validatePlaceName(' Home ')).toBeNull();
  });

  it('rejects very long names', () => {
    expect(validatePlaceName('x'.repeat(61))).not.toBeNull();
  });
});

describe('canRemovePlace', () => {
  it('lets the creator remove it', () => {
    expect(canRemovePlace(place('me'), 'me', 'MEMBER')).toBe(true);
  });

  it('lets an admin remove anyone’s place', () => {
    expect(canRemovePlace(place('someone'), 'me', 'ADMIN')).toBe(true);
  });

  it('stops other members', () => {
    expect(canRemovePlace(place('someone'), 'me', 'MEMBER')).toBe(false);
  });
});

describe('eventText', () => {
  it('describes arrivals and departures', () => {
    expect(eventText(event('ARRIVED'), 'mom')).toBe('Leo arrived at Home');
    expect(eventText(event('LEFT'), 'mom')).toBe('Leo left Home');
  });

  it('speaks to you about yourself', () => {
    expect(eventText(event('ARRIVED', 'me'), 'me')).toBe('You arrived at Home');
  });

  it('describes place changes', () => {
    expect(eventText(event('PLACE_ADDED'), 'mom')).toBe('Leo saved Home');
    expect(changesPlaces(event('PLACE_REMOVED'))).toBe(true);
    expect(changesPlaces(event('ARRIVED'))).toBe(false);
  });
});

describe('formatRadius', () => {
  it('uses meters, then kilometers', () => {
    expect(formatRadius(200)).toBe('200 m');
    expect(formatRadius(1000)).toBe('1 km');
    expect(formatRadius(1500)).toBe('1.5 km');
  });
});
