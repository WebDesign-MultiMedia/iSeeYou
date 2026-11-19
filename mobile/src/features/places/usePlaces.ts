import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from '@/api/client';
import type { Place } from '@/api/types';

/** A circle's saved places. Call `reload` when a PLACE_ADDED / PLACE_REMOVED event arrives. */
export function usePlaces(familyId: string | null) {
  const [places, setPlaces] = useState<Place[]>([]);
  const familyRef = useRef(familyId);
  useEffect(() => {
    familyRef.current = familyId;
  }, [familyId]);

  const reload = useCallback(async () => {
    const id = familyRef.current;
    if (!id) return;
    try {
      const list = await api.places(id);
      if (familyRef.current === id) setPlaces(list);
    } catch {
      // Places are extras on top of the map; a failed load just leaves the last list showing.
    }
  }, []);

  const [shownFamily, setShownFamily] = useState(familyId);
  if (shownFamily !== familyId) {
    setShownFamily(familyId);
    setPlaces([]);
  }

  useEffect(() => {
    void reload();
  }, [familyId, reload]);

  return { places, reload };
}
