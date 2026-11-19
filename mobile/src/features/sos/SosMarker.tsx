import { memo } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { Circle, Marker } from 'react-native-maps';
import type { SosAlert } from '@/api/types';

interface Props {
  alert: SosAlert & { lat: number; lng: number };
}

/** Where an SOS was sent from: a red pin above everything else, with its accuracy halo. */
export const SosMarker = memo(function SosMarker({ alert }: Props) {
  const center = { latitude: alert.lat, longitude: alert.lng };
  return (
    <>
      {alert.accuracy != null && alert.accuracy > 20 ? (
        <Circle center={center} radius={alert.accuracy} strokeColor="#c5221f99" fillColor="#c5221f22" />
      ) : null}
      <Marker
        coordinate={center}
        anchor={{ x: 0.5, y: 1 }}
        zIndex={1000}
        tracksViewChanges={false}
        accessibilityLabel={`SOS from ${alert.userName}`}
      >
        <View style={styles.pin}>
          <Text style={styles.text}>🆘 {alert.userName}</Text>
        </View>
      </Marker>
    </>
  );
});

const styles = StyleSheet.create({
  pin: {
    backgroundColor: '#c5221f',
    borderColor: '#ffffff',
    borderWidth: 2,
    borderRadius: 12,
    paddingHorizontal: 8,
    paddingVertical: 4,
  },
  text: { color: '#ffffff', fontWeight: '800', fontSize: 13 },
});
