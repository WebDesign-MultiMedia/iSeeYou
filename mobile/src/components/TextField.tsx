import type { Ref } from 'react';
import { StyleSheet, Text, TextInput, View, type TextInputInstance, type TextInputProps } from 'react-native';
import { font, radius, spacing, useTheme } from '@/theme';

interface Props extends TextInputProps {
  label: string;
  error?: string;
  /** React 19: refs are regular props, no forwardRef needed. */
  ref?: Ref<TextInputInstance>;
}

/** Labeled input with an inline error that screen readers announce with the field. */
export function TextField({ label, error, style, ref, ...rest }: Props) {
  const t = useTheme();
  return (
    <View style={styles.wrap}>
      <Text style={[styles.label, { color: t.text }]}>{label}</Text>
      <TextInput
        ref={ref}
        accessibilityLabel={label}
        accessibilityHint={error}
        placeholderTextColor={t.textMuted}
        style={[
          styles.input,
          { color: t.text, backgroundColor: t.surface, borderColor: error ? t.danger : t.border },
          style,
        ]}
        {...rest}
      />
      {error ? (
        <Text accessibilityLiveRegion="polite" style={[styles.error, { color: t.danger }]}>
          {error}
        </Text>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { gap: spacing.xs },
  label: font.label,
  input: {
    ...font.body,
    minHeight: 48,
    borderWidth: 1,
    borderRadius: radius.md,
    paddingHorizontal: spacing.md,
  },
  error: font.caption,
});
