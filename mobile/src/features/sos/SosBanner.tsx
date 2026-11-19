import { useState } from 'react';
import { Alert, Pressable, StyleSheet, Text, View, type LayoutChangeEvent } from 'react-native';
import type { SosAlert } from '@/api/types';
import { describeError } from '@/features/forms';
import { font, radius, spacing } from '@/theme';
import { hasLocation, sortForDisplay, sosDetail, sosTitle } from './sos';

interface Props {
  alerts: SosAlert[];
  myUserId: string | undefined;
  now: number;
  top: number;
  onShow: (alert: SosAlert) => void;
  onResolve: (alert: SosAlert) => Promise<void>;
  onLayout?: (e: LayoutChangeEvent) => void;
}

/**
 * Stays on screen while any SOS is open in this circle. Same red in light and dark mode, on
 * purpose: this must never blend in. Helpers can jump to the person or mark it handled; the
 * sender sees "I'm OK".
 */
export function SosBanner({ alerts, myUserId, now, top, onShow, onResolve, onLayout }: Props) {
  const [busy, setBusy] = useState(false);
  if (alerts.length === 0) return null;
  const [first, ...rest] = sortForDisplay(alerts, myUserId);
  const mine = first.userId === myUserId;

  const resolve = () => {
    const run = async () => {
      setBusy(true);
      try {
        await onResolve(first);
      } catch (e) {
        Alert.alert('Couldn’t update the SOS', describeError(e).message ?? undefined);
      } finally {
        setBusy(false);
      }
    };
    if (mine) return void run();
    Alert.alert(`Mark ${first.userName}’s SOS as handled?`, 'Everyone in the circle will see that it’s handled.', [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Mark handled', onPress: () => void run() },
    ]);
  };

  return (
    <View
      style={[styles.banner, { top }]}
      onLayout={onLayout}
      accessibilityRole="alert"
      accessibilityLiveRegion="assertive"
    >
      <Text style={[font.body, styles.title]} accessibilityRole="header">
        🆘 {sosTitle(first, myUserId)}
      </Text>
      <Text style={[font.caption, styles.detail]}>{sosDetail(first, myUserId, now)}</Text>
      {rest.length > 0 ? (
        <Text style={[font.caption, styles.detail]}>
          +{rest.length} more: {rest.map((a) => a.userName).join(', ')}
        </Text>
      ) : null}
      <View style={styles.actions}>
        {!mine && hasLocation(first) ? (
          <Pressable accessibilityRole="button" onPress={() => onShow(first)} style={[styles.action, styles.primary]}>
            <Text style={[font.label, styles.primaryText]}>Show on map</Text>
          </Pressable>
        ) : null}
        <Pressable
          accessibilityRole="button"
          accessibilityState={{ busy }}
          disabled={busy}
          onPress={resolve}
          style={[styles.action, styles.secondary, busy && styles.dim]}
        >
          <Text style={[font.label, styles.secondaryText]}>{mine ? 'I’m OK' : 'Mark handled'}</Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  banner: {
    position: 'absolute',
    left: spacing.lg,
    right: spacing.lg,
    backgroundColor: '#b3261e',
    borderRadius: radius.md,
    padding: spacing.md,
    gap: spacing.xs,
    shadowColor: '#000',
    shadowOpacity: 0.3,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 3 },
    elevation: 10,
  },
  title: { color: '#ffffff', fontWeight: '800' },
  detail: { color: '#fde7e6' },
  actions: { flexDirection: 'row', gap: spacing.sm, marginTop: spacing.xs },
  action: { minHeight: 44, borderRadius: radius.sm, paddingHorizontal: spacing.md, justifyContent: 'center' },
  primary: { backgroundColor: '#ffffff' },
  primaryText: { color: '#b3261e' },
  secondary: { borderWidth: 1.5, borderColor: '#ffffff' },
  secondaryText: { color: '#ffffff' },
  dim: { opacity: 0.6 },
});
