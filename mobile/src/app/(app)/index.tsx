import { router, useFocusEffect, useLocalSearchParams } from 'expo-router';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, Alert, FlatList, Pressable, Share, StyleSheet, Text, Vibration, View } from 'react-native';
import MapView from 'react-native-maps';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';
import { api } from '@/api/client';
import type { CircleEvent, FamilySummary, LocationPoint, Member, Place, SosAlert } from '@/api/types';
import { useAuth } from '@/auth/AuthProvider';
import { Button } from '@/components/Button';
import { inviteLink } from '@/config';
import { describeError } from '@/features/forms';
import { CircleMenu } from '@/features/map/CircleMenu';
import { ConnectionBadge } from '@/features/map/ConnectionBadge';
import { MemberMarker } from '@/features/map/MemberMarker';
import { MemberRow } from '@/features/map/MemberRow';
import { SharingBar } from '@/features/map/SharingBar';
import { useNow } from '@/features/map/useNow';
import { EventBanner } from '@/features/places/EventBanner';
import { PlaceOverlay } from '@/features/places/PlaceOverlay';
import { canRemovePlace, changesPlaces, formatRadius } from '@/features/places/places';
import { usePlaces } from '@/features/places/usePlaces';
import { sendSos } from '@/features/sos/sendSos';
import { hasLocation } from '@/features/sos/sos';
import { SosBanner } from '@/features/sos/SosBanner';
import { SosButton } from '@/features/sos/SosButton';
import { SosMarker } from '@/features/sos/SosMarker';
import { useSos } from '@/features/sos/useSos';
import { useLocationSharing } from '@/location/LocationSharingProvider';
import { disablePush, enablePush, pushEnabled } from '@/notifications/push';
import { useLiveCircle } from '@/realtime/useLiveCircle';
import { font, radius, spacing, useTheme } from '@/theme';

type Located = Member & { lastLocation: LocationPoint };
const isLocated = (m: Member): m is Located => m.sharingEnabled && m.lastLocation != null;

/** Picks which circle to show: the one we were sent to, else the current one, else the first. */
function pickCircle(list: FamilySummary[], requested: string | undefined, current: string | null): string | null {
  const ids = new Set(list.map((c) => c.id));
  if (requested && ids.has(requested)) return requested;
  if (current && ids.has(current)) return current;
  return list[0]?.id ?? null;
}

/** Home screen: the family on a full-screen map, with the roster in a panel below. */
export default function MapHome() {
  const { familyId: requestedId } = useLocalSearchParams<{ familyId?: string }>();
  const { user, signOut, pendingInvite, setPendingInvite } = useAuth();
  const t = useTheme();
  const insets = useSafeAreaInsets();
  const now = useNow();
  const sharing = useLocationSharing();

  const [circles, setCircles] = useState<FamilySummary[] | null>(null);
  const [circlesError, setCirclesError] = useState<string | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [menuOpen, setMenuOpen] = useState(false);
  const [panelHeight, setPanelHeight] = useState(0);

  // Opened from an invite link before signing in: continue to the join screen now.
  useEffect(() => {
    if (!pendingInvite) return;
    setPendingInvite(null);
    router.push({ pathname: '/join', params: { code: pendingInvite } });
  }, [pendingInvite, setPendingInvite]);

  const loadCircles = useCallback(async () => {
    try {
      const list = await api.myCircles();
      setCircles(list);
      setCirclesError(null);
      setSelectedId((current) => pickCircle(list, requestedId, current));
    } catch (e) {
      setCirclesError(describeError(e).message);
    }
  }, [requestedId]);

  // Reload whenever the screen regains focus, e.g. after joining or creating a circle.
  useFocusEffect(
    useCallback(() => {
      void loadCircles();
    }, [loadCircles]),
  );

  // ---- places + live events ----
  const { places, reload: reloadPlaces } = usePlaces(selectedId);
  const [banner, setBanner] = useState<CircleEvent | null>(null);
  const onCircleEvent = useCallback(
    (event: CircleEvent) => {
      if (changesPlaces(event)) void reloadPlaces();
      setBanner(event);
    },
    [reloadPlaces],
  );
  const dismissBanner = useCallback(() => setBanner(null), []);

  const [alertsOn, setAlertsOn] = useState(false);
  useEffect(() => {
    void pushEnabled().then(setAlertsOn);
  }, []);

  // ---- SOS ----
  const { alerts: sosAlerts, apply: applySosAlert, reload: reloadSos } = useSos(selectedId);
  const [sosHeight, setSosHeight] = useState(0);
  const onSos = useCallback(
    (alert: SosAlert) => {
      // A brand-new SOS from someone else: buzz, even though the banner is already on screen.
      if (alert.active && alert.userId !== user?.id && alert.createdAt === alert.updatedAt) {
        Vibration.vibrate([0, 400, 200, 400]);
      }
      applySosAlert(alert);
    },
    [applySosAlert, user?.id],
  );

  const liveHandlers = useMemo(
    () => ({
      onEvent: onCircleEvent,
      onSos,
      onReconnect: () => {
        void reloadSos();
        void reloadPlaces();
      },
    }),
    [onCircleEvent, onSos, reloadSos, reloadPlaces],
  );
  const { circle, error: circleError, connection, reload, applyMember } = useLiveCircle(selectedId, liveHandlers);
  const currentName = circle?.name ?? circles?.find((c) => c.id === selectedId)?.name ?? 'Your circle';

  const members = useMemo(() => {
    const list = circle?.members ?? [];
    // You first, then everyone else in the order they joined.
    return [...list].sort((a, b) => Number(b.userId === user?.id) - Number(a.userId === user?.id));
  }, [circle, user?.id]);
  const located = useMemo(() => members.filter(isLocated), [members]);
  const me = members.find((m) => m.userId === user?.id) ?? null;

  // ---- map camera ----
  const mapRef = useRef<MapView>(null);
  const fittedCircle = useRef<string | null>(null);

  // Frame everyone once per circle, when the first locations arrive.
  useEffect(() => {
    if (!circle || located.length === 0 || fittedCircle.current === circle.id || panelHeight === 0) return;
    fittedCircle.current = circle.id;
    const coords = located.map((m) => ({ latitude: m.lastLocation.lat, longitude: m.lastLocation.lng }));
    if (coords.length === 1) {
      mapRef.current?.animateToRegion({ ...coords[0], latitudeDelta: 0.02, longitudeDelta: 0.02 }, 400);
    } else {
      mapRef.current?.fitToCoordinates(coords, {
        edgePadding: { top: insets.top + 100, right: 60, bottom: 60, left: 60 },
        animated: true,
      });
    }
  }, [circle, located, panelHeight, insets.top]);

  const focusMember = useCallback((m: Member) => {
    if (!m.lastLocation || !m.sharingEnabled) return;
    mapRef.current?.animateToRegion(
      { latitude: m.lastLocation.lat, longitude: m.lastLocation.lng, latitudeDelta: 0.01, longitudeDelta: 0.01 },
      500,
    );
  }, []);

  const focusPlace = useCallback(
    (placeId: string) => {
      const p = places.find((x) => x.id === placeId);
      if (!p) return;
      const delta = Math.max(0.005, (p.radiusM / 111_000) * 4);
      mapRef.current?.animateToRegion(
        { latitude: p.lat, longitude: p.lng, latitudeDelta: delta, longitudeDelta: delta },
        500,
      );
    },
    [places],
  );

  // ---- actions ----
  const addPlaceAt = (lat: number, lng: number) => {
    if (!selectedId) return;
    router.push({ pathname: '/place-new', params: { familyId: selectedId, lat: String(lat), lng: String(lng), circleName: currentName } });
  };

  const openPlace = (place: Place) => {
    const removable = canRemovePlace(place, user?.id, circle?.myRole);
    Alert.alert(place.name, `Alerts when someone arrives or leaves (${formatRadius(place.radiusM)} around it).`, [
      { text: 'OK', style: 'cancel' },
      ...(removable
        ? [
            {
              text: 'Remove place',
              style: 'destructive' as const,
              onPress: async () => {
                try {
                  await api.deletePlace(place.familyId, place.id);
                  await reloadPlaces();
                } catch (e) {
                  Alert.alert('Couldn’t remove it', describeError(e).message ?? undefined);
                }
              },
            },
          ]
        : []),
    ]);
  };

  const showSos = (alert: SosAlert) => {
    if (!hasLocation(alert)) return;
    mapRef.current?.animateToRegion(
      { latitude: alert.lat, longitude: alert.lng, latitudeDelta: 0.01, longitudeDelta: 0.01 },
      500,
    );
  };

  const confirmDeleteHistory = () => {
    setMenuOpen(false);
    Alert.alert(
      'Delete your location history?',
      'Every location FamilyMap has stored for you is erased now, and your marker disappears from all your circles. If sharing is on, new locations start a fresh history.',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Delete',
          style: 'destructive',
          onPress: async () => {
            try {
              const { deleted } = await api.deleteMyHistory();
              Alert.alert('Deleted', `${deleted} ${deleted === 1 ? 'location was' : 'locations were'} erased.`);
            } catch (e) {
              Alert.alert('Couldn’t delete', describeError(e).message ?? undefined);
            }
          },
        },
      ],
    );
  };

  const toggleAlerts = async () => {
    setMenuOpen(false);
    if (alertsOn) {
      await disablePush();
      setAlertsOn(false);
      return;
    }
    const result = await enablePush();
    if (result.ok) setAlertsOn(true);
    else Alert.alert('Notifications', result.message);
  };

  const shareInvite = () => {
    if (!circle) return;
    void Share.share({
      message: `Join "${circle.name}" on FamilyMap. Open ${inviteLink(circle.inviteCode)} or enter the code ${circle.inviteCode}.`,
    });
  };

  const confirmLeave = () => {
    const current = circles?.find((c) => c.id === selectedId);
    if (!current) return;
    setMenuOpen(false);
    Alert.alert(
      `Leave ${current.name}?`,
      'You will stop sharing your location with this circle and won’t see its members anymore.',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Leave',
          style: 'destructive',
          onPress: async () => {
            try {
              await api.leaveCircle(current.id);
              setSelectedId(null);
              fittedCircle.current = null;
              await loadCircles();
            } catch (e) {
              Alert.alert('Could not leave', describeError(e).message ?? undefined);
            }
          },
        },
      ],
    );
  };

  // ---- empty / error states ----
  if (circles === null) {
    return (
      <SafeAreaView style={[styles.center, { backgroundColor: t.bg }]}>
        {circlesError ? (
          <>
            <Text style={[font.body, styles.centerText, { color: t.text }]}>{circlesError}</Text>
            <Button title="Try again" onPress={loadCircles} />
            <Button title="Sign out" variant="ghost" onPress={signOut} />
          </>
        ) : (
          <ActivityIndicator accessibilityLabel="Loading your circles" />
        )}
      </SafeAreaView>
    );
  }

  if (circles.length === 0) {
    return (
      <SafeAreaView style={[styles.center, { backgroundColor: t.bg }]}>
        <Text accessibilityRole="header" style={[font.title, styles.centerText, { color: t.text }]}>
          Hi {user?.name?.split(' ')[0] ?? 'there'} 👋
        </Text>
        <Text style={[font.body, styles.centerText, { color: t.textMuted }]}>
          Create a circle for your family, or join one with the code someone shared with you.
        </Text>
        <Button title="Create or join a circle" onPress={() => router.push('/join')} />
        <Button title="Sign out" variant="ghost" onPress={signOut} />
      </SafeAreaView>
    );
  }


  return (
    <View style={styles.flex}>
      <MapView
        ref={mapRef}
        style={StyleSheet.absoluteFill}
        // Keeps the map's logo/legal text and centering clear of the roster panel.
        mapPadding={{ top: insets.top, right: 0, bottom: panelHeight, left: 0 }}
        // The OS blue dot: instant feedback, before our own point has made the round trip.
        showsUserLocation={sharing.permission === 'granted'}
        showsMyLocationButton={sharing.permission === 'granted'}
        showsCompass={false}
        toolbarEnabled={false}
        accessibilityLabel="Family map. Long-press to save a place."
        onLongPress={(e) => addPlaceAt(e.nativeEvent.coordinate.latitude, e.nativeEvent.coordinate.longitude)}
      >
        {places.map((p) => (
          <PlaceOverlay key={p.id} place={p} onPress={openPlace} />
        ))}
        {sosAlerts.filter(hasLocation).map((a) => (
          <SosMarker key={a.id} alert={a} />
        ))}
        {located.map((m) => (
          <MemberMarker key={m.userId} member={m} now={now} onPress={focusMember} />
        ))}
      </MapView>

      {/* Top bar: circle switcher + live status */}
      <View style={[styles.topBar, { top: insets.top + spacing.sm }]} pointerEvents="box-none">
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={`Circle: ${currentName}. Opens circle menu`}
          onPress={() => setMenuOpen(true)}
          style={[styles.pill, { backgroundColor: t.card, shadowColor: t.shadow }]}
        >
          <Text style={[font.label, { color: t.text }]} numberOfLines={1}>
            {currentName} ▾
          </Text>
        </Pressable>
        <ConnectionBadge connection={connection} />
      </View>

      <SosBanner
        alerts={sosAlerts}
        myUserId={user?.id}
        now={now}
        top={insets.top + spacing.sm + 48}
        onShow={showSos}
        onResolve={async (a) => applySosAlert(await api.resolveSos(a.id))}
        onLayout={(e) => setSosHeight(e.nativeEvent.layout.height)}
      />

      <EventBanner
        event={banner}
        myUserId={user?.id}
        top={insets.top + spacing.sm + 48 + (sosAlerts.length > 0 ? sosHeight + spacing.sm : 0)}
        onPress={(e) => {
          dismissBanner();
          focusPlace(e.placeId);
        }}
        onDismiss={dismissBanner}
      />

      {panelHeight > 0 ? (
        <SosButton bottom={panelHeight + spacing.lg} onSend={async () => applySosAlert(await sendSos())} />
      ) : null}

      {/* Roster panel */}
      <View
        onLayout={(e) => setPanelHeight(e.nativeEvent.layout.height)}
        style={[styles.panel, { backgroundColor: t.card, paddingBottom: insets.bottom, shadowColor: t.shadow }]}
      >
        <View style={styles.panelHeader}>
          <View style={styles.flex}>
            <Text accessibilityRole="header" style={[font.heading, { color: t.text }]} numberOfLines={1}>
              {currentName}
            </Text>
            <Text style={[font.caption, { color: t.textMuted }]}>
              {members.length} {members.length === 1 ? 'member' : 'members'}
              {circle ? `  ·  Code ${circle.inviteCode}` : ''}
              {`  ·  ${places.length === 0 ? 'Long-press the map to save a place' : `${places.length} ${places.length === 1 ? 'place' : 'places'}`}`}
            </Text>
          </View>
          <Button title="Invite" variant="secondary" onPress={shareInvite} disabled={!circle} />
        </View>

        {selectedId ? (
          <SharingBar
            familyId={selectedId}
            circleName={currentName}
            me={me}
            now={now}
            onMemberChanged={applyMember}
          />
        ) : null}

        {circleError && !circle ? (
          <View style={styles.panelMessage}>
            <Text style={[font.body, { color: t.text }]}>{describeError(circleError).message}</Text>
            <Button title="Retry" variant="ghost" onPress={reload} />
          </View>
        ) : !circle ? (
          <ActivityIndicator style={styles.panelMessage} />
        ) : (
          <FlatList
            data={members}
            keyExtractor={(m) => m.userId}
            renderItem={({ item }) => (
              <MemberRow member={item} isMe={item.userId === user?.id} now={now} onPress={focusMember} />
            )}
            style={styles.list}
          />
        )}
      </View>

      <CircleMenu
        visible={menuOpen}
        circles={circles}
        selectedId={selectedId}
        onSelect={(id) => {
          setSelectedId(id);
          setMenuOpen(false);
        }}
        onJoinOrCreate={() => {
          setMenuOpen(false);
          router.push('/join');
        }}
        onLeave={confirmLeave}
        onSignOut={() => {
          setMenuOpen(false);
          void signOut();
        }}
        onClose={() => setMenuOpen(false)}
        alertsOn={alertsOn}
        onToggleAlerts={() => void toggleAlerts()}
        onDeleteHistory={confirmDeleteHistory}
      />
    </View>
  );
}

const shadow = {
  shadowOpacity: 0.15,
  shadowRadius: 8,
  shadowOffset: { width: 0, height: 2 },
  elevation: 4,
};

const styles = StyleSheet.create({
  flex: { flex: 1 },
  center: { flex: 1, justifyContent: 'center', padding: spacing.xl, gap: spacing.lg },
  centerText: { textAlign: 'center' },
  topBar: {
    position: 'absolute',
    left: spacing.lg,
    right: spacing.lg,
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: spacing.sm,
  },
  pill: {
    flexShrink: 1,
    height: 40,
    justifyContent: 'center',
    paddingHorizontal: spacing.lg,
    borderRadius: radius.pill,
    ...shadow,
  },
  panel: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    maxHeight: '45%',
    borderTopLeftRadius: radius.lg,
    borderTopRightRadius: radius.lg,
    paddingTop: spacing.lg,
    ...shadow,
  },
  panelHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    paddingHorizontal: spacing.lg,
    paddingBottom: spacing.sm,
  },
  panelMessage: { padding: spacing.lg, gap: spacing.sm },
  list: { flexGrow: 0 },
});
