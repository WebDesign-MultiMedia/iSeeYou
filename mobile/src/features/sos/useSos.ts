import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from '@/api/client';
import type { SosAlert } from '@/api/types';
import { applySos } from './sos';

/** Open SOS alerts in a circle: loaded over REST, kept current by `apply` from the live topic. */
export function useSos(familyId: string | null) {
  const [alerts, setAlerts] = useState<SosAlert[]>([]);
  const familyRef = useRef(familyId);
  useEffect(() => {
    familyRef.current = familyId;
  }, [familyId]);

  const reload = useCallback(async () => {
    const id = familyRef.current;
    if (!id) return;
    try {
      const list = await api.activeSos(id);
      if (familyRef.current === id) setAlerts(list);
    } catch {
      // Keep showing what we have; the live topic and the next reconnect will catch up.
    }
  }, []);

  const apply = useCallback((alert: SosAlert) => setAlerts((list) => applySos(list, alert)), []);

  const [shownFamily, setShownFamily] = useState(familyId);
  if (shownFamily !== familyId) {
    setShownFamily(familyId);
    setAlerts([]);
  }

  useEffect(() => {
    void reload();
  }, [familyId, reload]);

  return { alerts, apply, reload };
}
