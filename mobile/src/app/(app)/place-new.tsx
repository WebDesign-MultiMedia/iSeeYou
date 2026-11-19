import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { api } from '@/api/client';
import { Button } from '@/components/Button';
import { FormError, FormScreen } from '@/components/FormScreen';
import { TextField } from '@/components/TextField';
import { describeError } from '@/features/forms';
import { DEFAULT_RADIUS_M, MAX_NAME_LENGTH, RADIUS_OPTIONS, validatePlaceName } from '@/features/places/places';
import { font, radius, spacing, useTheme } from '@/theme';

/** Save the spot the user long-pressed on the map as a place for the whole circle. */
export default function NewPlace() {
  const params = useLocalSearchParams<{ familyId: string; lat: string; lng: string; circleName?: string }>();
  const t = useTheme();
  const [name, setName] = useState('');
  const [radiusM, setRadiusM] = useState<number>(DEFAULT_RADIUS_M);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const lat = Number(params.lat);
  const lng = Number(params.lng);
  const circleName = params.circleName ?? 'your circle';

  const save = async () => {
    const problem = validatePlaceName(name);
    if (problem) return setError(problem);
    if (!params.familyId || !Number.isFinite(lat) || !Number.isFinite(lng)) {
      return setError('Something went wrong picking the spot. Close this and long-press the map again.');
    }
    setBusy(true);
    setError(null);
    try {
      await api.createPlace(params.familyId, { name: name.trim(), lat, lng, radiusM });
      router.back();
    } catch (e) {
      const { message, fields } = describeError(e);
      setError(fields.name ?? message ?? 'Couldn’t save this place.');
      setBusy(false);
    }
  };

  return (
    <FormScreen
      title="Save this place"
      subtitle={`Everyone in ${circleName} gets an alert when someone arrives or leaves.`}
    >
      <TextField
        label="Name"
        placeholder="Home, School, Grandma’s…"
        value={name}
        onChangeText={setName}
        maxLength={MAX_NAME_LENGTH}
        autoFocus
        returnKeyType="done"
        onSubmitEditing={save}
      />

      <View style={styles.group} accessibilityRole="radiogroup" accessibilityLabel="Size">
        <Text style={[font.label, { color: t.text }]}>Size</Text>
        <View style={styles.options}>
          {RADIUS_OPTIONS.map((o) => {
            const selected = o.meters === radiusM;
            return (
              <Pressable
                key={o.meters}
                accessibilityRole="radio"
                accessibilityState={{ selected }}
                accessibilityLabel={`${o.label}, ${o.meters} meters, ${o.hint}`}
                onPress={() => setRadiusM(o.meters)}
                style={[
                  styles.option,
                  { borderColor: selected ? t.primary : t.border, backgroundColor: selected ? t.surface : t.card },
                ]}
              >
                <Text style={[font.label, { color: selected ? t.primary : t.text }]}>{o.label}</Text>
                <Text style={[font.caption, { color: t.textMuted }]}>{o.meters} m</Text>
                <Text style={[font.caption, styles.center, { color: t.textMuted }]}>{o.hint}</Text>
              </Pressable>
            );
          })}
        </View>
      </View>

      <Text style={[font.caption, { color: t.textMuted }]}>
        Alerts only use locations people already share with this circle. Anyone who has paused sharing
        doesn’t trigger them.
      </Text>

      <FormError message={error} />
      <Button title="Save place" onPress={save} loading={busy} />
      <Button title="Cancel" variant="ghost" onPress={() => router.back()} disabled={busy} />
    </FormScreen>
  );
}

const styles = StyleSheet.create({
  group: { gap: spacing.sm },
  options: { flexDirection: 'row', gap: spacing.sm },
  option: {
    flex: 1,
    minHeight: 88,
    borderWidth: 2,
    borderRadius: radius.md,
    padding: spacing.sm,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 2,
  },
  center: { textAlign: 'center' },
});
