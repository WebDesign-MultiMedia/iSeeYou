import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { api } from '@/api/client';
import { Button } from '@/components/Button';
import { FormError, FormScreen } from '@/components/FormScreen';
import { TextField } from '@/components/TextField';
import { describeError } from '@/features/forms';
import { font, radius, spacing, useTheme } from '@/theme';

/** Join a circle with an invite code (typed, or filled from a deep link) or create a new one. */
export default function JoinOrCreate() {
  const { code: linkedCode } = useLocalSearchParams<{ code?: string }>();
  const t = useTheme();
  const [code, setCode] = useState(linkedCode ?? '');
  const [name, setName] = useState('');
  const [busy, setBusy] = useState<'join' | 'create' | null>(null);
  const [joinError, setJoinError] = useState<string | null>(null);
  const [createError, setCreateError] = useState<string | null>(null);

  const openCircle = (familyId: string) => router.navigate({ pathname: '/', params: { familyId } });

  const join = async () => {
    if (!code.trim()) return setJoinError('Enter the code someone shared with you.');
    setBusy('join');
    setJoinError(null);
    try {
      openCircle((await api.joinCircle(code.trim())).id);
    } catch (e) {
      setJoinError(describeError(e).message ?? 'That code didn’t work.');
      setBusy(null);
    }
  };

  const create = async () => {
    if (!name.trim()) return setCreateError('Give your circle a name.');
    setBusy('create');
    setCreateError(null);
    try {
      openCircle((await api.createCircle(name.trim())).id);
    } catch (e) {
      const { message, fields } = describeError(e);
      setCreateError(fields.name ?? message);
      setBusy(null);
    }
  };

  const card = [styles.card, { backgroundColor: t.card, borderColor: t.border }];

  return (
    <FormScreen title="Your family circle" subtitle="Everyone in a circle can see each other on the map.">
      <View style={card}>
        <Text accessibilityRole="header" style={[font.heading, { color: t.text }]}>
          Join with a code
        </Text>
        <TextField
          label="Invite code"
          placeholder="K7QM-3XPA"
          value={code}
          onChangeText={setCode}
          autoCapitalize="characters"
          autoCorrect={false}
          maxLength={16}
          returnKeyType="go"
          onSubmitEditing={join}
          style={styles.code}
        />
        <FormError message={joinError} />
        <Button title="Join circle" onPress={join} loading={busy === 'join'} disabled={busy !== null} />
      </View>

      <Text style={[font.label, styles.or, { color: t.textMuted }]}>or</Text>

      <View style={card}>
        <Text accessibilityRole="header" style={[font.heading, { color: t.text }]}>
          Start a new circle
        </Text>
        <TextField
          label="Circle name"
          placeholder="The Smiths"
          value={name}
          onChangeText={setName}
          maxLength={100}
          returnKeyType="go"
          onSubmitEditing={create}
        />
        <FormError message={createError} />
        <Button
          title="Create circle"
          variant="secondary"
          onPress={create}
          loading={busy === 'create'}
          disabled={busy !== null}
        />
      </View>

      {router.canGoBack() ? <Button title="Cancel" variant="ghost" onPress={() => router.back()} /> : null}
    </FormScreen>
  );
}

const styles = StyleSheet.create({
  card: { borderWidth: 1, borderRadius: radius.lg, padding: spacing.lg, gap: spacing.md },
  code: { letterSpacing: 2, fontWeight: '600' },
  or: { textAlign: 'center' },
});
