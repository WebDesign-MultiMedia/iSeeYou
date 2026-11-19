import { ActivityIndicator, Pressable, StyleSheet, Text, type PressableProps } from 'react-native';
import { font, radius, spacing, useTheme } from '@/theme';

interface Props extends Omit<PressableProps, 'children'> {
  title: string;
  variant?: 'primary' | 'secondary' | 'ghost';
  loading?: boolean;
}

/** 48 dp tall (above the 44/48 minimum touch target), with a pressed state and a busy state. */
export function Button({ title, variant = 'primary', loading = false, disabled, style, ...rest }: Props) {
  const t = useTheme();
  const isDisabled = disabled || loading;
  const bg = variant === 'primary' ? t.primary : variant === 'secondary' ? t.surface : 'transparent';
  const fg = variant === 'primary' ? t.primaryText : t.primary;

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={title}
      accessibilityState={{ disabled: isDisabled, busy: loading }}
      disabled={isDisabled}
      style={(state) => [
        styles.base,
        { backgroundColor: bg, borderColor: variant === 'secondary' ? t.border : 'transparent' },
        state.pressed && { opacity: 0.8 },
        isDisabled && { opacity: 0.5 },
        typeof style === 'function' ? style(state) : style,
      ]}
      {...rest}
    >
      {loading ? <ActivityIndicator color={fg} /> : <Text style={[styles.label, { color: fg }]}>{title}</Text>}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: {
    minHeight: 48,
    borderRadius: radius.md,
    borderWidth: 1,
    paddingHorizontal: spacing.lg,
    alignItems: 'center',
    justifyContent: 'center',
  },
  label: { ...font.body, fontWeight: '600' },
});
