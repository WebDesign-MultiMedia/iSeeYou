import { Link } from 'expo-router';
import { useRef, useState } from 'react';
import { StyleSheet, Text, type TextInputInstance } from 'react-native';
import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { FormError, FormScreen } from '@/components/FormScreen';
import { TextField } from '@/components/TextField';
import { describeError } from '@/features/forms';
import { font, useTheme } from '@/theme';

export default function Register() {
  const { register } = useAuth();
  const t = useTheme();
  const emailRef = useRef<TextInputInstance>(null);
  const passwordRef = useRef<TextInputInstance>(null);
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fields, setFields] = useState<Record<string, string>>({});

  const submit = async () => {
    // Quick client-side checks; the server validates again and its messages win.
    const local: Record<string, string> = {};
    if (!name.trim()) local.name = 'Enter your name.';
    if (!email.trim()) local.email = 'Enter your email.';
    if (password.length < 8) local.password = 'Use at least 8 characters.';
    setFields(local);
    if (Object.keys(local).length) return;

    setBusy(true);
    setError(null);
    try {
      await register(name, email, password);
    } catch (e) {
      const { message, fields: serverFields } = describeError(e);
      setError(message);
      setFields(serverFields);
      setBusy(false);
    }
  };

  return (
    <FormScreen title="Create your account" subtitle="Your family will see your name on the map.">
      <TextField
        label="Name"
        value={name}
        onChangeText={setName}
        error={fields.name}
        autoComplete="name"
        textContentType="name"
        returnKeyType="next"
        onSubmitEditing={() => emailRef.current?.focus()}
      />
      <TextField
        ref={emailRef}
        label="Email"
        value={email}
        onChangeText={setEmail}
        error={fields.email}
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
        error={fields.password}
        secureTextEntry
        autoComplete="new-password"
        textContentType="newPassword"
        returnKeyType="go"
        onSubmitEditing={submit}
      />
      <FormError message={error} />
      <Button title="Create account" onPress={submit} loading={busy} />
      <Text style={[font.body, styles.center, { color: t.textMuted }]}>
        Already have an account?{' '}
        <Link href="/sign-in" style={{ color: t.primary, fontWeight: '600' }}>
          Sign in
        </Link>
      </Text>
    </FormScreen>
  );
}

const styles = StyleSheet.create({ center: { textAlign: 'center' } });
