import { Image, StyleSheet, Text, View } from 'react-native';
import { colorFor, initials } from '@/features/map/members';

interface Props {
  userId: string;
  name: string;
  avatarUrl: string | null;
  size?: number;
  /** Greys the avatar out, e.g. when the member has paused sharing. */
  dimmed?: boolean;
}

/** Photo if the user has one, otherwise their initials on their stable color. */
export function Avatar({ userId, name, avatarUrl, size = 40, dimmed = false }: Props) {
  const color = dimmed ? '#80868b' : colorFor(userId);
  const box = { width: size, height: size, borderRadius: size / 2 };
  return (
    <View
      accessible={false}
      style={[styles.ring, box, { borderColor: color, backgroundColor: color, opacity: dimmed ? 0.7 : 1 }]}
    >
      {avatarUrl ? (
        <Image source={{ uri: avatarUrl }} style={[box, styles.image]} />
      ) : (
        <Text style={[styles.initials, { fontSize: size * 0.4 }]}>{initials(name)}</Text>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  ring: { alignItems: 'center', justifyContent: 'center', borderWidth: 2, overflow: 'hidden' },
  image: { position: 'absolute' },
  initials: { color: '#ffffff', fontWeight: '700' },
});
