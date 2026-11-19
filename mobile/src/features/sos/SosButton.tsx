import { useEffect, useRef, useState } from 'react';
import { Alert, Animated, Easing, Pressable, StyleSheet, Text, Vibration, View } from 'react-native';
import { describeError } from '@/features/forms';
import { HOLD_TO_SEND_MS } from './sos';

interface Props {
  bottom: number;
  /** Sends the SOS; resolves when the server accepted it. */
  onSend: () => Promise<void>;
}

const SIZE = 64;
const RED = '#c5221f';

/**
 * Hold for 2 seconds to send an SOS. The ring fills while held and a short buzz confirms.
 * Letting go early cancels. Screen-reader users get a "Send SOS" action with a confirmation
 * instead, because press-and-hold is awkward with VoiceOver/TalkBack.
 */
export function SosButton({ bottom, onSend }: Props) {
  const [progress] = useState(() => new Animated.Value(0));
  const [scale] = useState(() => progress.interpolate({ inputRange: [0, 1], outputRange: [0.001, 1] }));
  const [sending, setSending] = useState(false);
  const holding = useRef<Animated.CompositeAnimation | null>(null);

  useEffect(() => () => holding.current?.stop(), []);

  const send = async () => {
    setSending(true);
    try {
      await onSend();
    } catch (e) {
      Alert.alert('SOS not sent', `${describeError(e).message ?? 'Something went wrong.'} Try again, or call for help.`);
    } finally {
      setSending(false);
      progress.setValue(0);
    }
  };

  const start = () => {
    if (sending) return;
    holding.current = Animated.timing(progress, {
      toValue: 1,
      duration: HOLD_TO_SEND_MS,
      easing: Easing.linear,
      useNativeDriver: true,
    });
    holding.current.start(({ finished }) => {
      if (!finished) return;
      Vibration.vibrate(400);
      void send();
    });
  };

  const cancel = () => {
    if (sending) return;
    holding.current?.stop();
    Animated.timing(progress, { toValue: 0, duration: 150, useNativeDriver: true }).start();
  };

  const confirmForScreenReader = () =>
    Alert.alert('Send SOS?', 'All your circles will be alerted with your location.', [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Send SOS', style: 'destructive', onPress: () => void send() },
    ]);

  return (
    <View style={[styles.wrap, { bottom }]} pointerEvents="box-none">
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="SOS"
        accessibilityHint="Hold for two seconds to alert all your circles with your location, even circles you've paused."
        accessibilityState={{ busy: sending }}
        accessibilityActions={[{ name: 'activate', label: 'Send SOS' }]}
        onAccessibilityAction={(e) => e.nativeEvent.actionName === 'activate' && confirmForScreenReader()}
        onPressIn={start}
        onPressOut={cancel}
        style={styles.button}
      >
        <Animated.View style={[styles.fill, { transform: [{ scale }] }]} />
        <Text style={styles.label}>{sending ? '…' : 'SOS'}</Text>
      </Pressable>
      <Text style={styles.hint} importantForAccessibility="no">
        Hold
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { position: 'absolute', right: 16, alignItems: 'center' },
  button: {
    width: SIZE,
    height: SIZE,
    borderRadius: SIZE / 2,
    backgroundColor: RED,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
    borderWidth: 3,
    borderColor: '#ffffff',
    shadowColor: '#000',
    shadowOpacity: 0.3,
    shadowRadius: 8,
    shadowOffset: { width: 0, height: 3 },
    elevation: 8,
  },
  fill: { position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, borderRadius: SIZE / 2, backgroundColor: '#7a1210' },
  label: { color: '#ffffff', fontSize: 18, fontWeight: '800', letterSpacing: 1 },
  hint: {
    marginTop: 4,
    color: '#ffffff',
    fontSize: 11,
    fontWeight: '700',
    backgroundColor: 'rgba(0,0,0,0.55)',
    paddingHorizontal: 6,
    borderRadius: 6,
    overflow: 'hidden',
  },
});
