/** Mirrors the backend DTOs (com.familymap.dto). Keep in sync with the API. */

export type Role = 'ADMIN' | 'MEMBER';

export interface User {
  id: string;
  name: string;
  email: string;
  avatarUrl: string | null;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  user: User;
}

export interface LocationPoint {
  userId: string;
  lat: number;
  lng: number;
  accuracy: number | null;
  battery: number | null;
  /** ISO-8601 instant */
  recordedAt: string;
}

/** Body of POST /api/locations. */
export interface LocationReport {
  lat: number;
  lng: number;
  accuracy: number | null;
  battery: number | null;
  /** ISO-8601; lets offline points upload later with their real time */
  recordedAt: string;
}

/** shared=false: sharing is paused in every circle, so the server dropped the point. */
export interface RecordLocationResponse {
  shared: boolean;
  sharedWith: string[];
  location: LocationPoint | null;
}

export interface Member {
  userId: string;
  name: string;
  avatarUrl: string | null;
  role: Role;
  sharingEnabled: boolean;
  joinedAt: string;
  /** null when the member has no points yet or has paused sharing */
  lastLocation: LocationPoint | null;
}

export interface FamilySummary {
  id: string;
  name: string;
  inviteCode: string;
  myRole: Role;
  memberCount: number;
  createdAt: string;
}

export interface FamilyDetail {
  id: string;
  name: string;
  inviteCode: string;
  myRole: Role;
  createdAt: string;
  members: Member[];
}

export interface Place {
  id: string;
  familyId: string;
  name: string;
  lat: number;
  lng: number;
  radiusM: number;
  createdBy: string;
  createdAt: string;
}

export type CircleEventType = 'ARRIVED' | 'LEFT' | 'PLACE_ADDED' | 'PLACE_REMOVED';

/** Live on /topic/family/{id}/events. */
export interface CircleEvent {
  type: CircleEventType;
  familyId: string;
  placeId: string;
  placeName: string;
  userId: string;
  userName: string;
  at: string;
}

/** RFC 9457 problem+json, as returned by GlobalExceptionHandler. */
export interface Problem {
  status: number;
  title?: string;
  detail?: string;
  errors?: Record<string, string>;
}
