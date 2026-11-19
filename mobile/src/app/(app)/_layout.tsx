import * as Notifications from 'expo-notifications';
import { router, Stack } from 'expo-router';
import { useEffect } from 'react';
import { familyIdFrom } from '@/notifications/push';

export default function AppLayout() {
  // Tapping an arrival notification opens that circle, whether the app was running or not.
  const lastResponse = Notifications.useLastNotificationResponse();
  useEffect(() => {
    const familyId = familyIdFrom(lastResponse);
    if (familyId) router.navigate({ pathname: '/', params: { familyId } });
  }, [lastResponse]);

  return (
    <Stack screenOptions={{ headerShown: false }}>
      <Stack.Screen name="index" />
      <Stack.Screen name="join" options={{ presentation: 'modal' }} />
      <Stack.Screen name="share-location" options={{ presentation: 'modal' }} />
      <Stack.Screen name="place-new" options={{ presentation: 'modal' }} />
    </Stack>
  );
}
