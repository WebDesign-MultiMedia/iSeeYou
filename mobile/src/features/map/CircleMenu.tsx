import { Modal, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import type { FamilySummary } from '@/api/types';
import { font, radius, spacing, useTheme } from '@/theme';

interface Props {
  visible: boolean;
  circles: FamilySummary[];
  selectedId: string | null;
  onSelect: (familyId: string) => void;
  onJoinOrCreate: () => void;
  onLeave: () => void;
  onSignOut: () => void;
  onClose: () => void;
}

/** Bottom sheet: switch circle, join or create another, leave, sign out. */
export function CircleMenu(props: Props) {
  const { visible, circles, selectedId, onSelect, onJoinOrCreate, onLeave, onSignOut, onClose } = props;
  const t = useTheme();
  const insets = useSafeAreaInsets();

  const Item = ({ label, onPress, danger, selected }: {
    label: string; onPress: () => void; danger?: boolean; selected?: boolean;
  }) => (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ selected }}
      onPress={onPress}
      style={({ pressed }) => [styles.item, pressed && { backgroundColor: t.surface }]}
    >
      <Text style={[font.body, { color: danger ? t.danger : t.text, fontWeight: selected ? '700' : '400' }]}>
        {selected ? '✓  ' : ''}
        {label}
      </Text>
    </Pressable>
  );

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <Pressable accessibilityLabel="Close menu" style={[styles.backdrop, { backgroundColor: t.overlay }]} onPress={onClose} />
      <View style={[styles.sheet, { backgroundColor: t.card, paddingBottom: insets.bottom + spacing.md }]}>
        <View style={[styles.handle, { backgroundColor: t.border }]} />
        <Text accessibilityRole="header" style={[font.label, styles.section, { color: t.textMuted }]}>
          YOUR CIRCLES
        </Text>
        <ScrollView style={styles.list}>
          {circles.map((c) => (
            <Item
              key={c.id}
              label={`${c.name}  ·  ${c.memberCount} ${c.memberCount === 1 ? 'member' : 'members'}`}
              selected={c.id === selectedId}
              onPress={() => onSelect(c.id)}
            />
          ))}
        </ScrollView>
        <View style={[styles.divider, { backgroundColor: t.border }]} />
        <Item label="Join or create a circle" onPress={onJoinOrCreate} />
        {selectedId ? <Item label="Leave this circle" onPress={onLeave} danger /> : null}
        <Item label="Sign out" onPress={onSignOut} />
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1 },
  sheet: { borderTopLeftRadius: radius.lg, borderTopRightRadius: radius.lg, paddingTop: spacing.sm },
  handle: { alignSelf: 'center', width: 40, height: 4, borderRadius: 2, marginBottom: spacing.sm },
  section: { paddingHorizontal: spacing.lg, paddingVertical: spacing.sm },
  list: { maxHeight: 260 },
  item: { minHeight: 52, justifyContent: 'center', paddingHorizontal: spacing.lg },
  divider: { height: StyleSheet.hairlineWidth, marginVertical: spacing.sm },
});
