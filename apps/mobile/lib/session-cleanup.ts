export type SessionCleanupDependencies = {
  unregisterPushDevice: () => Promise<void>;
  clearTracking: () => Promise<void>;
  onError?: (scope: 'push' | 'tracking', error: unknown) => void;
};

/**
 * Creates the single-flight cleanup used by every terminal session path.
 * Tracking cleanup is in a finally block so a push unregister failure cannot
 * leave location updates or GPS data behind.
 */
export function createSessionCleanup({ unregisterPushDevice, clearTracking, onError }: SessionCleanupDependencies) {
  let inFlight: Promise<void> | null = null;

  const report = (scope: 'push' | 'tracking', error: unknown) => {
    if (onError) {
      onError(scope, error);
      return;
    }
    console.warn(`Failed to clean up ${scope} session data:`, error);
  };

  return (): Promise<void> => {
    if (inFlight) return inFlight;

    inFlight = (async () => {
      try {
        await unregisterPushDevice();
      } catch (error) {
        report('push', error);
      } finally {
        try {
          await clearTracking();
        } catch (error) {
          report('tracking', error);
        }
      }
    })().finally(() => {
      inFlight = null;
    });

    return inFlight;
  };
}
