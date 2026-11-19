import { router } from 'expo-router';
import { useState } from 'react';
import { Linking, StyleSheet, Text, View } from 'react-native';
import { Button } from '@/components/Button';
import { FormError, FormScreen } from '@/components/FormScreen';
import { useLocationSharing } from '@/location/LocationSharingProvider';
import { font, radius, spacing, useTheme } from '@/theme';

const POINTS = [
  ['👪', 'Only your circles', 'People in the family circles you joined see your location. Nobody else, ever.'],
  ['⏸️', 'Pause any time', 'Pause a circle and nobody there sees where you are, not even your last spot.'],
  ['🔋', 'Battery included', 'Your battery level is shared so family can tell a dead phone from a quiet one. Tracking slows down when your battery is low.'],
  ['🗓️', 'Kept for 7 days', 'Old locations are deleted automatically.'],
] as const;

/**
 * The explanation shown before the OS permission prompt. The user decides here, with the
 * facts in front of them; the OS prompt then just confirms it.
 */
export default function ShareLocation() {
  const t = useTheme();
  const { enable, permission, canUseBackground } = useLocationSharing();
  const [busy, setBusy] = useState<'open' | 'always' | null>(null);
  const [error, setError] = useState<string | null>(null);

  const start = async (background: boolean) => {
    setBusy(background ? 'always' : 'open');
    setError(null);
    try {
      if (await enable({ background })) router.back();
      else setError('Location access wasn’t allowed, so nothing is shared.');
    } catch {
      setError('Couldn’t start location sharing. Please try again.');
    } finally {
      setBusy(null);
    }
  };

  if (permission === 'blocked') {
    return (
      <FormScreen
        title="Location is turned off"
        subtitle="FamilyMap isn’t allowed to use your location. You can change that in Settings, under FamilyMap → Location."
      >
        <Button title="Open Settings" onPress={() => void Linking.openSettings()} />
        <Button title="Not now" variant="ghost" onPress={() => router.back()} />
      </FormScreen>
    );
  }

  return (
    <FormScreen title="Share your location" subtitle="Here’s exactly what happens when you turn it on.">
      <View style={[styles.card, { backgroundColor: t.card, borderColor: t.border }]}>
        {POINTS.map(([icon, title, body]) => (
          <View key={title} style={styles.point} accessible accessibilityLabel={`${title}. ${body}`}>
            <Text style={styles.icon} importantForAccessibility="no">
              {icon}
            </Text>
            <View style={styles.flex}>
              <Text style={[font.body, styles.bold, { color: t.text }]}>{title}</Text>
              <Text style={[font.caption, { color: t.textMuted }]}>{body}</Text>
            </View>
          </View>
        ))}
      </View>

      <FormError message={error} />

      {canUseBackground ? (
        <>
          <Button
            title="Share, even when the app is closed"
            onPress={() => start(true)}
            loading={busy === 'always'}
            disabled={busy !== null}
          />
          <Text style={[font.caption, styles.center, { color: t.textMuted }]}>
            Your phone will show that FamilyMap is using your location while it runs in the background.
          </Text>
          <Button
            title="Only while I’m using the app"
            variant="secondary"
            onPress={() => start(false)}
            loading={busy === 'open'}
            disabled={busy !== null}
          />
        </>
      ) : (
        <>
          <Button
            title="Share while I’m using the app"
            onPress={() => start(false)}
            loading={busy === 'open'}
            disabled={busy !== null}
          />
          <Text style={[font.caption, styles.center, { color: t.textMuted }]}>
            Sharing in the background needs the installed FamilyMap app. In Expo Go, it shares while the app is open.
          </Text>
        </>
      )}
      <Button title="Not now" variant="ghost" onPress={() => router.back()} disabled={busy !== null} />
    </FormScreen>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  card: { borderWidth: 1, borderRadius: radius.lg, padding: spacing.lg, gap: spacing.lg },
  point: { flexDirection: 'row', gap: spacing.md, alignItems: 'flex-start' },
  icon: { fontSize: 22, width: 28, textAlign: 'center' },
  bold: { fontWeight: '600' },
  center: { textAlign: 'center' },
});
