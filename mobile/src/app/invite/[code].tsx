import { Redirect, useLocalSearchParams } from 'expo-router';
import { useEffect } from 'react';
import { useAuth } from '@/auth/AuthProvider';

/**
 * Handles `familymap://invite/K7QM-3XPA`.
 *
 * Signed in: go straight to the join screen with the code filled in.
 * Signed out: remember the code, then sign in. The map screen picks it up afterwards, so
 * someone who installs the app from an invite never has to retype it.
 */
export default function InviteLink() {
  const { code } = useLocalSearchParams<{ code: string }>();
  const { status, setPendingInvite } = useAuth();
  const signedIn = status === 'signedIn';

  useEffect(() => {
    if (code && !signedIn) setPendingInvite(code);
  }, [code, signedIn, setPendingInvite]);

  if (signedIn) return <Redirect href={{ pathname: '/join', params: { code } }} />;
  return <Redirect href="/sign-in" />;
}
