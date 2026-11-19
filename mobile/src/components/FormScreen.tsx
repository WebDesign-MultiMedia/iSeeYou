import type { ReactNode } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { font, spacing, useTheme } from '@/theme';

interface Props {
  title: string;
  subtitle?: string;
  children: ReactNode;
}

/** Layout for form screens: safe area, scrolls above the keyboard, consistent header. */
export function FormScreen({ title, subtitle, children }: Props) {
  const t = useTheme();
  return (
    <SafeAreaView style={[styles.flex, { backgroundColor: t.bg }]} edges={['top', 'bottom']}>
      <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
        <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
          <View style={styles.header}>
            <Text accessibilityRole="header" style={[font.title, { color: t.text }]}>
              {title}
            </Text>
            {subtitle ? <Text style={[font.body, { color: t.textMuted }]}>{subtitle}</Text> : null}
          </View>
          {children}
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

/** A form-level error (e.g. "Invalid email or password."), announced to screen readers. */
export function FormError({ message }: { message: string | null }) {
  const t = useTheme();
  if (!message) return null;
  return (
    <Text accessibilityLiveRegion="assertive" accessibilityRole="alert" style={[font.body, { color: t.danger }]}>
      {message}
    </Text>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  content: { flexGrow: 1, padding: spacing.xl, gap: spacing.lg, justifyContent: 'center' },
  header: { gap: spacing.sm, marginBottom: spacing.sm },
});
