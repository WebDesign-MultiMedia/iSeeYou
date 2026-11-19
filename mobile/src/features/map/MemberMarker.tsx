import { memo, useEffect, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { Circle, Marker } from 'react-native-maps';
import type { LocationPoint, Member } from '@/api/types';
import { Avatar } from '@/components/Avatar';
import { colorFor, statusLine } from './members';

interface Props {
  member: Member & { lastLocation: LocationPoint };
  now: number;
  onPress: (member: Member) => void;
}

/** Below this accuracy (meters) the halo is just noise, so it isn't drawn. */
const SHOW_ACCURACY_ABOVE_M = 30;

/**
 * A member's avatar pinned at their latest location, with a name label and, when the fix is
 * imprecise, a translucent accuracy circle so nobody over-trusts a rough position.
 */
export const MemberMarker = memo(function MemberMarker({ member, now, onPress }: Props) {
  const { lat, lng, accuracy } = member.lastLocation;
  const color = colorFor(member.userId);

  // Custom marker views are snapshotted into bitmaps. Re-snapshot briefly whenever the
  // content changes, then stop: tracking permanently costs CPU on every map frame (Android).
  const [tracking, setTracking] = useState(true);
  useEffect(() => {
    setTracking(true);
    const id = setTimeout(() => setTracking(false), 500);
    return () => clearTimeout(id);
  }, [member.name, member.avatarUrl, member.lastLocation.recordedAt]);

  return (
    <>
      {accuracy != null && accuracy > SHOW_ACCURACY_ABOVE_M ? (
        <Circle
          center={{ latitude: lat, longitude: lng }}
          radius={accuracy}
          strokeColor={`${color}66`}
          fillColor={`${color}1A`}
        />
      ) : null}
      <Marker
        coordinate={{ latitude: lat, longitude: lng }}
        anchor={{ x: 0.5, y: 0.5 }}
        tracksViewChanges={tracking}
        onPress={() => onPress(member)}
        accessibilityLabel={`${member.name}, ${statusLine(member, now)}`}
      >
        <View style={styles.wrap}>
          <Avatar userId={member.userId} name={member.name} avatarUrl={member.avatarUrl} size={44} />
          <View style={[styles.label, { backgroundColor: color }]}>
            <Text style={styles.labelText} numberOfLines={1}>
              {member.name}
            </Text>
          </View>
        </View>
      </Marker>
    </>
  );
});

const styles = StyleSheet.create({
  wrap: { alignItems: 'center' },
  label: { marginTop: 2, paddingHorizontal: 6, paddingVertical: 1, borderRadius: 8, maxWidth: 110 },
  labelText: { color: '#fff', fontSize: 12, fontWeight: '700' },
});
