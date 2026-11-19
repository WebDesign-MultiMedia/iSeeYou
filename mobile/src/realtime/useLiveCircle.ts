import { Client, type StompSubscription } from '@stomp/stompjs';
import { useCallback, useEffect, useRef, useState } from 'react';
import { AppState } from 'react-native';
import { api, freshAccessToken } from '@/api/client';
import type { CircleEvent, FamilyDetail, LocationPoint, Member } from '@/api/types';
import { WS_URL } from '@/config';
import { applyLocation, applyMemberUpdate } from '@/features/map/members';

export type Connection = 'connecting' | 'live' | 'reconnecting' | 'offline';

interface LiveCircle {
  circle: FamilyDetail | null;
  loading: boolean;
  error: Error | null;
  connection: Connection;
  reload: () => Promise<void>;
  /** Merge a member we got from our own request (e.g. after pausing) without waiting for the echo. */
  applyMember: (member: Member) => void;
}

/**
 * A circle's roster with locations kept up to date in realtime.
 *
 * 1. Loads the circle over REST (members + latest points).
 * 2. Opens a STOMP connection and subscribes to `/topic/family/{id}/locations` (each point is
 *    merged with {@link applyLocation}) and `/topic/family/{id}/members` (pause/resume, merged
 *    with {@link applyMemberUpdate}) and `/topic/family/{id}/events` (arrivals, departures,
 *    place changes, handed to `onEvent`).
 * 3. On every (re)connect it reloads over REST, so points sent while the socket was down
 *    aren't lost.
 * 4. Disconnects while the app is in the background, which saves battery and data, and
 *    reconnects when it comes back.
 */
export function useLiveCircle(familyId: string | null, onEvent?: (event: CircleEvent) => void): LiveCircle {
  const [circle, setCircle] = useState<FamilyDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [connection, setConnection] = useState<Connection>('connecting');
  const familyRef = useRef(familyId);
  const onEventRef = useRef(onEvent);
  useEffect(() => {
    familyRef.current = familyId;
    onEventRef.current = onEvent;
  }, [familyId, onEvent]);

  // Switching circles: clear the old roster while rendering, not in an effect, so the
  // previous circle's members never flash on screen (React's "adjust state on prop change").
  const [shownFamily, setShownFamily] = useState(familyId);
  if (shownFamily !== familyId) {
    setShownFamily(familyId);
    setCircle(null);
    setLoading(true);
    setConnection('connecting');
  }

  const reload = useCallback(async () => {
    const id = familyRef.current;
    if (!id) return;
    try {
      const detail = await api.circle(id);
      if (familyRef.current === id) {
        setCircle(detail);
        setError(null);
      }
    } catch (e) {
      if (familyRef.current === id) setError(e as Error);
    } finally {
      if (familyRef.current === id) setLoading(false);
    }
  }, []);

  const mergeMember = useCallback((member: Member) => {
    setCircle((c) => {
      if (!c) return c;
      const members = applyMemberUpdate(c.members, member);
      return members === c.members ? c : { ...c, members };
    });
  }, []);

  useEffect(() => {
    if (!familyId) return;
    void reload();

    let subscriptions: StompSubscription[] = [];
    const client = new Client({
      brokerURL: WS_URL,
      reconnectDelay: 3_000,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      // React Native's WebSocket mangles text frames that contain NUL; these two flags are
      // the stompjs-documented workaround.
      forceBinaryWSFrames: true,
      appendMissingNULLonIncoming: true,
      // Runs before every (re)connect, so a long-lived socket always presents a valid token.
      beforeConnect: async (c) => {
        const token = await freshAccessToken();
        if (!token) {
          await c.deactivate(); // signed out
          return;
        }
        c.connectHeaders = { Authorization: `Bearer ${token}` };
      },
      onConnect: () => {
        setConnection('live');
        subscriptions = [
          client.subscribe(`/topic/family/${familyId}/locations`, (message) => {
            const point = JSON.parse(message.body) as LocationPoint;
            setCircle((c) => {
              if (!c) return c;
              const members = applyLocation(c.members, point);
              return members === c.members ? c : { ...c, members };
            });
          }),
          client.subscribe(`/topic/family/${familyId}/members`, (message) => {
            mergeMember(JSON.parse(message.body) as Member);
          }),
          client.subscribe(`/topic/family/${familyId}/events`, (message) => {
            onEventRef.current?.(JSON.parse(message.body) as CircleEvent);
          }),
        ];
        void reload(); // catch up on anything missed while disconnected
      },
      onWebSocketClose: () => setConnection((s) => (s === 'offline' ? s : 'reconnecting')),
      onStompError: () => {
        // The server rejected us (e.g. we left this circle). Retrying won't help.
        setConnection('offline');
        void client.deactivate();
        void reload();
      },
    });
    client.activate();

    const appState = AppState.addEventListener('change', (state) => {
      if (state === 'active' && !client.active) {
        setConnection('connecting');
        client.activate();
      } else if (state === 'background' && client.active) {
        subscriptions.forEach((sub) => sub.unsubscribe());
        void client.deactivate();
      }
    });

    return () => {
      appState.remove();
      subscriptions.forEach((sub) => sub.unsubscribe());
      void client.deactivate();
    };
  }, [familyId, reload, mergeMember]);

  return { circle, loading, error, connection, reload, applyMember: mergeMember };
}
