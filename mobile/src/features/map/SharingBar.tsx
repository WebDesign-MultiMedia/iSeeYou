import { router } from 'expo-router';
import { useState } from 'react';
import { Alert, StyleSheet, Text, View } from 'react-native';
import { api } from '@/api/client';
import type { Member } from '@/api/types';
import { Button } from '@/components/Button';
import { describeError } from '@/features/forms';
import { useLocationSharing } from '@/location/LocationSharingProvider';
import { POWER_MODE_LABEL } from '@/location/policy';
import { font, spacing, useTheme } from '@/theme';
import { lastSeenLabel } from './members';

interface Props {
  familyId: string;
  circleName: string;
  /** My row in this circle's roster (null while loading). */
  me: Member | null;
  now: number;
  onMemberChanged: (member: Member) => void;
}

/**
 * "Am I sharing?", always visible above the roster: what's happening right now and the one
 * action that changes it (share, pause, resume, or fix permissions).
 */
export function SharingBar({ familyId, circleName, me, now, onMemberChanged }: Props) {
  const t = useTheme();
  const sharing = useLocationSharing();
  const [busy, setBusy] = useState(false);

  const setCircleSharing = async (enabled: boolean) => {
    setBusy(true);
    try {
      onMemberChanged(await api.setSharing(familyId, enabled));
      if (enabled) await sharing.resume();
    } catch (e) {
      Alert.alert(enabled ? 'Couldn’t resume' : 'Couldn’t pause', describeError(e).message ?? undefined);
    } finally {
      setBusy(false);
    }
  };

  let dot = t.textMuted;
  let title: string;
  let detail: string | null = null;
  let action: { label: string; onPress: () => void; variant?: 'primary' | 'secondary' } | null = null;

  if (!sharing.consented || sharing.permission !== 'granted') {
    title = 'You’re not sharing your location';
    detail = `${circleName} can’t see you on the map.`;
    action = { label: 'Share', onPress: () => router.push('/share-location') };
  } else if (me && !me.sharingEnabled) {
    title = `Paused for ${circleName}`;
    detail = 'Nobody here can see where you are.';
    action = { label: 'Resume', onPress: () => void setCircleSharing(true) };
  } else {
    dot = sharing.mode === 'off' ? t.warning : t.success;
    title = sharing.mode === 'background' ? 'Sharing, even when closed' : 'Sharing while the app is open';
    const parts = [
      sharing.lastSentAt ? `Updated ${lastSeenLabel(sharing.lastSentAt, now).toLowerCase()}` : 'Finding you…',
    ];
    if (sharing.power !== 'normal') parts.push(POWER_MODE_LABEL[sharing.power]);
    if (sharing.pending > 0) parts.push(`${sharing.pending} waiting for signal`);
    detail = parts.join(' · ');
    action = { label: 'Pause', onPress: () => void setCircleSharing(false), variant: 'secondary' };
  }

  return (
    <View
      style={[styles.bar, { backgroundColor: t.surface, borderColor: t.border }]}
      accessibilityLiveRegion="polite"
    >
      <View style={[styles.dot, { backgroundColor: dot }]} importantForAccessibility="no" />
      <View style={styles.text} accessible accessibilityLabel={`${title}. ${detail ?? ''}`}>
        <Text style={[font.label, { color: t.text }]} numberOfLines={1}>
          {title}
        </Text>
        {detail ? (
          <Text style={[font.caption, { color: t.textMuted }]} numberOfLines={1}>
            {detail}
          </Text>
        ) : null}
      </View>
      {action ? (
        <Button
          title={action.label}
          variant={action.variant ?? 'primary'}
          onPress={action.onPress}
          loading={busy}
          style={styles.button}
        />
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  bar: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    marginHorizontal: spacing.lg,
    marginBottom: spacing.sm,
    paddingLeft: spacing.md,
    paddingRight: spacing.xs,
    paddingVertical: spacing.xs,
    borderWidth: 1,
    borderRadius: 14,
  },
  dot: { width: 10, height: 10, borderRadius: 5 },
  text: { flex: 1, gap: 2 },
  button: { minHeight: 40, paddingHorizontal: spacing.md },
});
