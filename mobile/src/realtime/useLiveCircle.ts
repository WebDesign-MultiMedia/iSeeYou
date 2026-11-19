import { Client, type StompSubscription } from '@stomp/stompjs';
import { useCallback, useEffect, useRef, useState } from 'react';
import { AppState } from 'react-native';
import { api, freshAccessToken } from '@/api/client';
import type { FamilyDetail, LocationPoint } from '@/api/types';
import { WS_URL } from '@/config';
import { applyLocation } from '@/features/map/members';

export type Connection = 'connecting' | 'live' | 'reconnecting' | 'offline';

interface LiveCircle {
  circle: FamilyDetail | null;
  loading: boolean;
  error: Error | null;
  connection: Connection;
  reload: () => Promise<void>;
}

/**
 * A circle's roster with locations kept up to date in realtime.
 *
 * 1. Loads the circle over REST (members + latest points).
 * 2. Opens a STOMP connection and subscribes to `/topic/family/{id}/locations`; each point is
 *    merged into the roster with {@link applyLocation}.
 * 3. On every (re)connect it reloads over REST, so points sent while the socket was down
 *    aren't lost.
 * 4. Disconnects while the app is in the background, which saves battery and data, and
 *    reconnects when it comes back.
 */
export function useLiveCircle(familyId: string | null): LiveCircle {
  const [circle, setCircle] = useState<FamilyDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [connection, setConnection] = useState<Connection>('connecting');
  const familyRef = useRef(familyId);
  familyRef.current = familyId;

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

  useEffect(() => {
    if (!familyId) return;
    setCircle(null);
    setLoading(true);
    setConnection('connecting');
    void reload();

    let subscription: StompSubscription | undefined;
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
        subscription = client.subscribe(`/topic/family/${familyId}/locations`, (message) => {
          const point = JSON.parse(message.body) as LocationPoint;
          setCircle((c) => {
            if (!c) return c;
            const members = applyLocation(c.members, point);
            return members === c.members ? c : { ...c, members };
          });
        });
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
        subscription?.unsubscribe();
        void client.deactivate();
      }
    });

    return () => {
      appState.remove();
      subscription?.unsubscribe();
      void client.deactivate();
    };
  }, [familyId, reload]);

  return { circle, loading, error, connection, reload };
}
