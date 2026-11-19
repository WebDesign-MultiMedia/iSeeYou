import { StyleSheet, Text, View } from 'react-native';
import type { Connection } from '@/realtime/useLiveCircle';
import { font, radius, spacing, useTheme } from '@/theme';

const LABEL: Record<Connection, string> = {
  connecting: 'Connecting…',
  live: 'Live',
  reconnecting: 'Reconnecting…',
  offline: 'Offline',
};

/** Tells the user whether what they're seeing is live, so stale positions aren't mistaken for current ones. */
export function ConnectionBadge({ connection }: { connection: Connection }) {
  const t = useTheme();
  const dot = connection === 'live' ? t.success : connection === 'offline' ? t.danger : t.warning;
  return (
    <View
      accessible
      accessibilityLabel={`Connection: ${LABEL[connection]}`}
      accessibilityLiveRegion="polite"
      style={[styles.pill, { backgroundColor: t.card, shadowColor: t.shadow }]}
    >
      <View style={[styles.dot, { backgroundColor: dot }]} />
      <Text style={[font.label, { color: t.text }]}>{LABEL[connection]}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  pill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.xs + 2,
    paddingHorizontal: spacing.md,
    height: 40,
    borderRadius: radius.pill,
    shadowOpacity: 0.15,
    shadowRadius: 6,
    shadowOffset: { width: 0, height: 2 },
    elevation: 3,
  },
  dot: { width: 10, height: 10, borderRadius: 5 },
});
