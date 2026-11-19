import type { CircleEvent, Place, Role } from '@/api/types';

/** Pure helpers for saved places and circle events, kept out of React for easy testing. */

/** Radius choices offered when saving a place, in meters (the API accepts 50-2000). */
export const RADIUS_OPTIONS = [
  { meters: 100, label: 'Small', hint: 'A house' },
  { meters: 200, label: 'Medium', hint: 'A school or office' },
  { meters: 500, label: 'Large', hint: 'A campus or park' },
] as const;

export const DEFAULT_RADIUS_M = 100;
export const MAX_NAME_LENGTH = 60;

/** null when valid, otherwise a message for the user. */
export function validatePlaceName(name: string): string | null {
  const trimmed = name.trim();
  if (!trimmed) return 'Give this place a name, like “Home” or “School”.';
  if (trimmed.length > MAX_NAME_LENGTH) return `Keep it under ${MAX_NAME_LENGTH} characters.`;
  return null;
}

/** Same rule as the server: whoever added it, or a circle admin. */
export function canRemovePlace(place: Place, myUserId: string | undefined, myRole: Role | undefined): boolean {
  return myRole === 'ADMIN' || (myUserId != null && place.createdBy === myUserId);
}

/** One line for the in-app banner, in the second person when it's about you. */
export function eventText(event: CircleEvent, myUserId: string | undefined): string {
  const you = event.userId === myUserId;
  const who = you ? 'You' : event.userName;
  switch (event.type) {
    case 'ARRIVED':
      return `${who} arrived at ${event.placeName}`;
    case 'LEFT':
      return `${who} left ${event.placeName}`;
    case 'PLACE_ADDED':
      return `${who} saved ${event.placeName}`;
    case 'PLACE_REMOVED':
      return `${who} removed ${event.placeName}`;
  }
}

export const eventIcon = (event: CircleEvent): string =>
  event.type === 'ARRIVED' ? '📍' : event.type === 'LEFT' ? '👋' : event.type === 'PLACE_ADDED' ? '⭐' : '🗑️';

/** Does this event change the list of places (so the map should reload them)? */
export const changesPlaces = (event: CircleEvent): boolean =>
  event.type === 'PLACE_ADDED' || event.type === 'PLACE_REMOVED';

/** "100 m", "1.5 km". */
export function formatRadius(meters: number): string {
  return meters < 1000 ? `${meters} m` : `${(meters / 1000).toFixed(meters % 1000 === 0 ? 0 : 1)} km`;
}
