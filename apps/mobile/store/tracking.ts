import { create } from 'zustand';
import * as Location from 'expo-location';
import { api } from '@/lib/api';
import { 
  startBackgroundTracking, 
  stopBackgroundTracking, 
  syncPendingLocations,
  clearLocalTrackingData,
  initDb,
  isTrackingQuarantined,
  DEFAULT_OPTIONS,
  BATTERY_SAVER_OPTIONS,
  getActiveTrackingSession,
  type LocationRow,
} from '@/lib/tracking-service';
import type { Race, GpsLocation, CheckpointRecord, LiveStats, TrackingState } from '@/types';

interface ExtendedTrackingState extends TrackingState {
  foregroundSubscription: Location.LocationSubscription | null;
  startTime: Date | null;
  syncInterval: ReturnType<typeof setInterval> | null;
}

const calculateStats = (
  locations: GpsLocation[],
  startTime: Date | null,
  race: Race | null
): LiveStats | null => {
  if (!locations.length || !startTime || !race) return null;

  const now = new Date();
  const elapsedSeconds = Math.floor((now.getTime() - startTime.getTime()) / 1000);
  
  let totalDistance = 0;
  for (let i = 1; i < locations.length; i++) {
    const prev = locations[i - 1];
    const curr = locations[i];
    totalDistance += haversineDistance(
      prev.latitude,
      prev.longitude,
      curr.latitude,
      curr.longitude
    );
  }

  const averagePace = totalDistance > 0 
    ? (elapsedSeconds / 60) / totalDistance 
    : 0;

  const recentLocations = locations.slice(-10);
  let recentDistance = 0;
  for (let i = 1; i < recentLocations.length; i++) {
    const prev = recentLocations[i - 1];
    const curr = recentLocations[i];
    recentDistance += haversineDistance(
      prev.latitude,
      prev.longitude,
      curr.latitude,
      curr.longitude
    );
  }
  
  const recentTime = recentLocations.length > 1
    ? (new Date(recentLocations[recentLocations.length - 1].timestamp).getTime() - 
       new Date(recentLocations[0].timestamp).getTime()) / 1000 / 60
    : 0;
  
  const currentPace = recentDistance > 0 ? recentTime / recentDistance : averagePace;

  const raceDistance = race.distanceKm ?? race.distance ?? 0;
  const remainingDistance = raceDistance - totalDistance;
  const estimatedRemainingSeconds = remainingDistance > 0 ? (remainingDistance * averagePace * 60) : 0;
  const estimatedFinish = new Date(now.getTime() + estimatedRemainingSeconds * 1000);

  return {
    currentPace,
    averagePace,
    distanceCovered: totalDistance,
    elapsedTime: elapsedSeconds,
    estimatedFinishTime: estimatedFinish.toISOString(),
  };
};

const haversineDistance = (lat1: number, lon1: number, lat2: number, lon2: number): number => {
  const R = 6371;
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLon = (lon2 - lon1) * Math.PI / 180;
  const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
};

export const useTrackingStore = create<ExtendedTrackingState>((set, get) => ({
  isTracking: false,
  currentRace: null,
  locations: [],
  checkpointRecords: [],
  liveStats: null,
  batterySaver: false,
  foregroundSubscription: null,
  startTime: null,
  syncInterval: null,

  recoverActiveTrackingSession: async () => {
    await initDb();
    const activeSession = await getActiveTrackingSession();
    if (!activeSession) return;

    try {
      const race = await api.getRace(activeSession.raceId);
      const startTime = new Date(activeSession.startedAt);
      set({
        currentRace: race,
        isTracking: true,
        batterySaver: activeSession.batterySaver === 1,
        startTime,
      });
      await get().rehydrateFromLocalDb(activeSession.raceId);
    } catch (error) {
      console.warn('Failed to recover active tracking session:', error);
      set({
        currentRace: null,
        isTracking: false,
        startTime: null,
      });
    }
  },

  setBatterySaver: (enabled: boolean) => {
    set({ batterySaver: enabled });
    const { isTracking, currentRace } = get();
    if (isTracking && currentRace) {
      get().stopTracking();
      get().startTracking(currentRace);
    }
  },

  rehydrateFromLocalDb: async (raceId: string) => {
    const db = await initDb();
    if (isTrackingQuarantined()) return;
    const rows = await db.getAllAsync<LocationRow>(
      'SELECT * FROM locations WHERE raceId = ? ORDER BY timestamp ASC',
      [raceId]
    );
    
    if (rows.length > 0) {
      const mappedLocations: GpsLocation[] = rows.map(r => ({
        latitude: r.latitude,
        longitude: r.longitude,
        altitude: r.altitude ?? undefined,
        accuracy: r.accuracy ?? undefined,
        speed: r.speed ?? undefined,
        heading: r.heading ?? undefined,
        timestamp: r.timestamp
      }));
      
      const { startTime, currentRace } = get();
      const liveStats = calculateStats(mappedLocations, startTime, currentRace);
      set({ locations: mappedLocations, liveStats });
    }
  },

  startTracking: async (race: Race) => {
    if (!race.id) throw new Error('Race ID is required');

    await initDb();

    const options = get().batterySaver ? BATTERY_SAVER_OPTIONS : DEFAULT_OPTIONS;
    await startBackgroundTracking(race.id, options, get().batterySaver);

    const startTime = new Date();
    set({ currentRace: race, isTracking: true, locations: [], checkpointRecords: [], startTime });

    // Initial rehydrate in case there are points already
    await get().rehydrateFromLocalDb(race.id);

    const subscription = await Location.watchPositionAsync(
      {
        accuracy: options.accuracy,
        timeInterval: options.timeInterval / 2,
        distanceInterval: options.distanceInterval / 2,
      },
      (location) => {
        const gpsLocation: GpsLocation = {
          latitude: location.coords.latitude,
          longitude: location.coords.longitude,
          altitude: location.coords.altitude ?? undefined,
          accuracy: location.coords.accuracy ?? undefined,
          speed: location.coords.speed ?? undefined,
          heading: location.coords.heading ?? undefined,
          timestamp: new Date(location.timestamp).toISOString(),
        };
        get().addLocation(gpsLocation);
      }
    );

    const syncInterval = setInterval(async () => {
      if (get().currentRace?.id) {
        await syncPendingLocations(get().currentRace!.id).catch(console.error);
        // After sync, rehydrate from DB to ensure state is consistent with what was saved in background
        await get().rehydrateFromLocalDb(get().currentRace!.id);
      }
    }, get().batterySaver ? 120000 : 60000);

    set({ foregroundSubscription: subscription, syncInterval });
  },

  stopTracking: () => {
    const { foregroundSubscription, syncInterval, currentRace } = get();
    
    stopBackgroundTracking().catch(console.error);

    if (foregroundSubscription) {
      foregroundSubscription.remove();
    }

    if (syncInterval) {
      clearInterval(syncInterval);
    }

    if (currentRace?.id) {
      syncPendingLocations(currentRace.id).catch(console.error);
    }

    set({
      isTracking: false,
      currentRace: null,
      foregroundSubscription: null,
      startTime: null,
      syncInterval: null,
    });
  },

  clearTrackingData: async () => {
    const { foregroundSubscription, syncInterval } = get();

    if (foregroundSubscription) {
      try {
        foregroundSubscription.remove();
      } catch (error) {
        console.warn('Failed to remove foreground tracking subscription:', error);
      }
    }
    if (syncInterval) clearInterval(syncInterval);

    try {
      await clearLocalTrackingData();
    } finally {
      set({
        isTracking: false,
        currentRace: null,
        locations: [],
        checkpointRecords: [],
        liveStats: null,
        foregroundSubscription: null,
        startTime: null,
        syncInterval: null,
        batterySaver: false,
      });
    }
  },

  addLocation: (location: GpsLocation) => {
    const { locations, startTime, currentRace } = get();
    // Use unique filter by timestamp to avoid duplicates between foreground and background sync
    const newLocations = [...locations, location].filter((loc, index, self) => 
      index === self.findIndex((t) => t.timestamp === loc.timestamp)
    );
    const liveStats = calculateStats(newLocations, startTime, currentRace);
    set({ locations: newLocations, liveStats });
  },

  recordCheckpoint: (record: CheckpointRecord) => {
    const { checkpointRecords, currentRace } = get();
    set({ checkpointRecords: [...checkpointRecords, record] });

    if (currentRace?.id) {
      api.recordCheckpoint(currentRace.id, record.checkpointId, record.method as 'qr_code' | 'gps')
        .catch(console.error);
    }
  },
}));
