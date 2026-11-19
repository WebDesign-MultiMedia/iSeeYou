import { memo } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { Circle, Marker } from 'react-native-maps';
import type { Place } from '@/api/types';
import { useTheme } from '@/theme';
import { formatRadius } from './places';

interface Props {
  place: Place;
  onPress: (place: Place) => void;
}

/** A saved place: its geofence as a dashed circle, and a small name tag at the center. */
export const PlaceOverlay = memo(function PlaceOverlay({ place, onPress }: Props) {
  const t = useTheme();
  const center = { latitude: place.lat, longitude: place.lng };
  return (
    <>
      <Circle
        center={center}
        radius={place.radiusM}
        strokeColor={`${t.success}99`}
        fillColor={`${t.success}14`}
        strokeWidth={2}
        lineDashPattern={[6, 6]}
      />
      <Marker
        coordinate={center}
        anchor={{ x: 0.5, y: 0.5 }}
        tracksViewChanges={false}
        onPress={() => onPress(place)}
        accessibilityLabel={`Saved place ${place.name}, ${formatRadius(place.radiusM)} radius`}
      >
        <View style={[styles.tag, { backgroundColor: t.card, borderColor: t.success }]}>
          <Text style={[styles.text, { color: t.text }]} numberOfLines={1}>
            ⭐ {place.name}
          </Text>
        </View>
      </Marker>
    </>
  );
});

const styles = StyleSheet.create({
  tag: { borderWidth: 1.5, borderRadius: 10, paddingHorizontal: 6, paddingVertical: 2, maxWidth: 140 },
  text: { fontSize: 12, fontWeight: '700' },
});
