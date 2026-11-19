import { SplashScreen, Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useEffect } from 'react';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { AuthProvider, useAuth } from '@/auth/AuthProvider';
import { useTheme } from '@/theme';

// Keep the splash screen up until we know whether the user is signed in, so there's no
// flash of the sign-in screen on launch.
void SplashScreen.preventAutoHideAsync();

export default function RootLayout() {
  return (
    <SafeAreaProvider>
      <AuthProvider>
        <RootNavigator />
      </AuthProvider>
    </SafeAreaProvider>
  );
}

/**
 * Auth gate. `Stack.Protected` makes the (auth) screens unreachable while signed in and the
 * (app) screens unreachable while signed out, and switches between them automatically when
 * the session changes, including when a refresh token expires.
 */
function RootNavigator() {
  const { status } = useAuth();
  const t = useTheme();
  const signedIn = status === 'signedIn';

  useEffect(() => {
    if (status !== 'loading') void SplashScreen.hideAsync();
  }, [status]);

  if (status === 'loading') return null; // splash screen is still visible

  return (
    <>
      <StatusBar style="auto" />
      <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: t.bg } }}>
        <Stack.Protected guard={signedIn}>
          <Stack.Screen name="(app)" />
        </Stack.Protected>
        <Stack.Protected guard={!signedIn}>
          <Stack.Screen name="(auth)" />
        </Stack.Protected>
        {/* Deep link target, reachable in both states. */}
        <Stack.Screen name="invite/[code]" />
      </Stack>
    </>
  );
}
