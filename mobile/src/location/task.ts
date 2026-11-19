import type { LocationObject } from 'expo-location';
import * as TaskManager from 'expo-task-manager';
import { LOCATION_TASK, onFixes } from './tracker';

/**
 * The background location task. TaskManager requires tasks to be defined at module scope,
 * imported from the app entry (`src/app/_layout.tsx`), so that when the OS wakes the app
 * in the background with new fixes, the handler already exists before any screen renders.
 */
try {
  if (!TaskManager.isTaskDefined(LOCATION_TASK)) {
    TaskManager.defineTask<{ locations: LocationObject[] }>(LOCATION_TASK, async ({ data, error }) => {
      if (error || !data?.locations?.length) return;
      await onFixes(data.locations);
    });
  }
} catch {
  // Expo Go on Android has no TaskManager; foreground tracking still works.
}
