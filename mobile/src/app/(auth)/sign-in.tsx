import { Link } from 'expo-router';
import { useRef, useState } from 'react';
import { StyleSheet, Text, type TextInputInstance } from 'react-native';
import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { FormError, FormScreen } from '@/components/FormScreen';
import { TextField } from '@/components/TextField';
import { describeError } from '@/features/forms';
import { font, useTheme } from '@/theme';

export default function SignIn() {
  const { signIn, pendingInvite } = useAuth();
  const t = useTheme();
  const passwordRef = useRef<TextInputInstance>(null);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    if (!email || !password) {
      setError('Enter your email and password.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await signIn(email, password);
      // On success the auth gate swaps to the map screen by itself.
    } catch (e) {
      setError(describeError(e).message ?? 'Invalid email or password.');
      setBusy(false);
    }
  };

  return (
    <FormScreen
      title="Welcome back"
      subtitle={pendingInvite ? 'Sign in to join your family circle.' : 'See where your family is, with their consent.'}
    >
      <TextField
        label="Email"
        value={email}
        onChangeText={setEmail}
        autoCapitalize="none"
        autoComplete="email"
        keyboardType="email-address"
        textContentType="emailAddress"
        returnKeyType="next"
        onSubmitEditing={() => passwordRef.current?.focus()}
      />
      <TextField
        ref={passwordRef}
        label="Password"
        value={password}
        onChangeText={setPassword}
        secureTextEntry
        autoComplete="current-password"
        textContentType="password"
        returnKeyType="go"
        onSubmitEditing={submit}
      />
      <FormError message={error} />
      <Button title="Sign in" onPress={submit} loading={busy} />
      <Text style={[font.body, styles.center, { color: t.textMuted }]}>
        New to FamilyMap?{' '}
        <Link href="/register" style={{ color: t.primary, fontWeight: '600' }}>
          Create an account
        </Link>
      </Text>
    </FormScreen>
  );
}

const styles = StyleSheet.create({ center: { textAlign: 'center' } });
