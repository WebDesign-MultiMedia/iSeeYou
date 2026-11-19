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

/** RFC 9457 problem+json, as returned by GlobalExceptionHandler. */
export interface Problem {
  status: number;
  title?: string;
  detail?: string;
  errors?: Record<string, string>;
}
