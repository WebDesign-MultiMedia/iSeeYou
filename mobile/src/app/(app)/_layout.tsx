import { Stack } from 'expo-router';

export default function AppLayout() {
  return (
    <Stack screenOptions={{ headerShown: false }}>
      <Stack.Screen name="index" />
      <Stack.Screen name="join" options={{ presentation: 'modal' }} />
      <Stack.Screen name="share-location" options={{ presentation: 'modal' }} />
    </Stack>
  );
}
