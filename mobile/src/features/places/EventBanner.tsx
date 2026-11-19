import { useEffect, useState } from 'react';
import { Animated, Pressable, StyleSheet, Text } from 'react-native';
import type { CircleEvent } from '@/api/types';
import { font, radius, spacing, useTheme } from '@/theme';
import { eventIcon, eventText } from './places';

interface Props {
  event: CircleEvent | null;
  myUserId: string | undefined;
  top: number;
  onPress: (event: CircleEvent) => void;
  onDismiss: () => void;
}

const VISIBLE_MS = 5_000;

/**
 * "Leo arrived at Home", slid in from the top for a few seconds. Announced to screen readers.
 * Tapping it centers the map on the place. The parent owns `event`; we call `onDismiss` after
 * fading out, and the parent clears it.
 */
export function EventBanner({ event, myUserId, top, onPress, onDismiss }: Props) {
  const t = useTheme();
  // Animated values are mutable objects, not render state; a lazy useState keeps one per mount.
  const [slide] = useState(() => new Animated.Value(0));
  const [offset] = useState(() => slide.interpolate({ inputRange: [0, 1], outputRange: [-20, 0] }));

  useEffect(() => {
    if (!event) return;
    slide.setValue(0);
    Animated.spring(slide, { toValue: 1, useNativeDriver: true }).start();
    const id = setTimeout(() => {
      Animated.timing(slide, { toValue: 0, duration: 200, useNativeDriver: true }).start(() => onDismiss());
    }, VISIBLE_MS);
    return () => clearTimeout(id);
  }, [event, slide, onDismiss]);

  if (!event) return null;
  const shown = event;
  const text = eventText(shown, myUserId);

  return (
    <Animated.View style={[styles.wrap, { top, opacity: slide, transform: [{ translateY: offset }] }]}>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={text}
        accessibilityHint="Shows the place on the map"
        accessibilityLiveRegion="polite"
        onPress={() => onPress(shown)}
        style={[styles.banner, { backgroundColor: t.card, shadowColor: t.shadow, borderColor: t.border }]}
      >
        <Text style={styles.icon} importantForAccessibility="no">
          {eventIcon(shown)}
        </Text>
        <Text style={[font.label, styles.text, { color: t.text }]} numberOfLines={2}>
          {text}
        </Text>
      </Pressable>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  wrap: { position: 'absolute', left: spacing.lg, right: spacing.lg },
  banner: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
    padding: spacing.md,
    borderRadius: radius.md,
    borderWidth: StyleSheet.hairlineWidth,
    shadowOpacity: 0.2,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 3 },
    elevation: 6,
  },
  icon: { fontSize: 20 },
  text: { flex: 1 },
});
