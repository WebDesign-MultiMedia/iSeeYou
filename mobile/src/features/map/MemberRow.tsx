import { memo } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import type { Member } from '@/api/types';
import { Avatar } from '@/components/Avatar';
import { font, spacing, useTheme, type Palette } from '@/theme';
import { batteryTone, statusLine, type BatteryTone } from './members';

interface Props {
  member: Member;
  isMe: boolean;
  now: number;
  onPress: (member: Member) => void;
}

const toneColor = (tone: BatteryTone, t: Palette) =>
  tone === 'critical' ? t.danger : tone === 'low' ? t.warning : t.textMuted;

/** One roster row: avatar, name, last seen, battery. Tapping centers the map on them. */
export const MemberRow = memo(function MemberRow({ member, isMe, now, onPress }: Props) {
  const t = useTheme();
  const hasLocation = member.sharingEnabled && member.lastLocation != null;
  const tone = batteryTone(member.lastLocation?.battery);
  const status = statusLine(member, now);

  return (
    <Pressable
      accessibilityRole={hasLocation ? 'button' : 'text'}
      accessibilityLabel={`${member.name}${isMe ? ' (you)' : ''}, ${status}`}
      accessibilityHint={hasLocation ? 'Shows them on the map' : undefined}
      disabled={!hasLocation}
      onPress={() => onPress(member)}
      style={({ pressed }) => [styles.row, pressed && { backgroundColor: t.surface }]}
    >
      <Avatar
        userId={member.userId}
        name={member.name}
        avatarUrl={member.avatarUrl}
        dimmed={!member.sharingEnabled}
      />
      <View style={styles.text}>
        <Text style={[font.body, styles.name, { color: t.text }]} numberOfLines={1}>
          {member.name}
          {isMe ? <Text style={{ color: t.textMuted, fontWeight: '400' }}> (you)</Text> : null}
          {member.role === 'ADMIN' ? <Text style={{ color: t.textMuted, fontWeight: '400' }}> · Admin</Text> : null}
        </Text>
        <Text
          style={[font.caption, { color: tone === 'ok' || tone === 'unknown' ? t.textMuted : toneColor(tone, t) }]}
          numberOfLines={1}
        >
          {status}
        </Text>
      </View>
      {!member.sharingEnabled ? (
        <View style={[styles.badge, { borderColor: t.border }]}>
          <Text style={[font.caption, { color: t.textMuted }]}>Paused</Text>
        </View>
      ) : null}
    </Pressable>
  );
});

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.lg,
    minHeight: 56,
  },
  text: { flex: 1, gap: 2 },
  name: { fontWeight: '600' },
  badge: { borderWidth: 1, borderRadius: 999, paddingHorizontal: spacing.sm, paddingVertical: 2 },
});
