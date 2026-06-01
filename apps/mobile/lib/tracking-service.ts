import * as Location from 'expo-location';
import * as TaskManager from 'expo-task-manager';
import { openDatabaseAsync, type TrackingDatabase } from './db';
import { api } from './api';

export const LOCATION_TRACKING_TASK = 'LOCATION_TRACKING_TASK';

export interface TrackingOptions {
  accuracy: Location.LocationAccuracy;
  timeInterval: number;
  distanceInterval: number;
}

export const DEFAULT_OPTIONS: TrackingOptions = {
  accuracy: Location.Accuracy.BestForNavigation,
  timeInterval: 10000, // 10 seconds
  distanceInterval: 10, // 10 meters
};

export const BATTERY_SAVER_OPTIONS: TrackingOptions = {
  accuracy: Location.Accuracy.Balanced,
  timeInterval: 60000, // 1 minute
  distanceInterval: 50, // 50 meters
};

export type LocationRow = {
  id: number;
  raceId: string;
  latitude: number;
  longitude: number;
  altitude: number | null;
  accuracy: number | null;
  speed: number | null;
  heading: number | null;
  timestamp: string;
  synced: number;
};

// Initialize SQLite Database
let db: TrackingDatabase | null = null;

export const initDb = async () => {
  if (db) return db;
  db = await openDatabaseAsync('tracking.db');
  await db.execAsync(`
    CREATE TABLE IF NOT EXISTS locations (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      raceId TEXT NOT NULL,
      latitude REAL NOT NULL,
      longitude REAL NOT NULL,
      altitude REAL,
      accuracy REAL,
      speed REAL,
      heading REAL,
      timestamp TEXT NOT NULL,
      synced INTEGER DEFAULT 0
    );
  `);
  return db;
};

// Simple Haversine for distance calculation (km)
const calculateDistance = (lat1: number, lon1: number, lat2: number, lon2: number): number => {
  const R = 6371;
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLon = (lon2 - lon1) * Math.PI / 180;
  const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
};

// Background Task Definition
TaskManager.defineTask(LOCATION_TRACKING_TASK, async ({ data, error }: any) => {
  if (error) {
    console.error('Background location error:', error);
    return;
  }
  if (data) {
    const { locations } = data;
    const database = await initDb();
    const raceId = await getActiveRaceId();

    if (!raceId) return;

    for (const location of locations) {
      await database.runAsync(
        'INSERT INTO locations (raceId, latitude, longitude, altitude, accuracy, speed, heading, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?)',
        [
          raceId,
          location.coords.latitude,
          location.coords.longitude,
          location.coords.altitude ?? null,
          location.coords.accuracy ?? null,
          location.coords.speed ?? null,
          location.coords.heading ?? null,
          new Date(location.timestamp).toISOString(),
        ]
      );
    }
    
    // Attempt to sync immediately if we have network, or wait for the sync worker
    await syncPendingLocations(raceId);
  }
});

// Simple state management for the active race ID in the background task
let activeRaceId: string | null = null;
export const setActiveRaceId = (id: string | null) => {
  activeRaceId = id;
};
const getActiveRaceId = () => activeRaceId;

export const syncPendingLocations = async (raceId: string) => {
  const database = await initDb();
  const pending = await database.getAllAsync<LocationRow>(
    'SELECT * FROM locations WHERE raceId = ? AND synced = 0 ORDER BY timestamp ASC LIMIT 100',
    [raceId]
  );

  if (pending.length === 0) return;

  // Differential Sync Logic: 
  // We filter points that are too close to each other to save bandwidth/server load.
  const filtered: LocationRow[] = [];
  let lastSentPoint: LocationRow | null = null;

  for (const point of pending) {
    if (!lastSentPoint) {
      filtered.push(point);
      lastSentPoint = point;
      continue;
    }

    const dist = calculateDistance(
      lastSentPoint.latitude, lastSentPoint.longitude,
      point.latitude, point.longitude
    );

    const timeDiff = new Date(point.timestamp).getTime() - new Date(lastSentPoint.timestamp).getTime();

    // Significant update criteria:
    // 1. Distance > 15 meters
    // 2. Speed changed significantly (> 3 km/h)
    // 3. Last update was more than 90 seconds ago (safety heartbeat)
    if (dist > 0.015 || Math.abs((point.speed || 0) - (lastSentPoint.speed || 0)) > 3 || timeDiff > 90000) {
      filtered.push(point);
      lastSentPoint = point;
    }
  }

  // Mark all pending as processed regardless of whether they were filtered
  // This ensures we don't keep reprocessing the same "redundant" points.
  const allIds = pending.map(p => p.id).join(',');

  if (filtered.length === 0) {
    await database.runAsync(`UPDATE locations SET synced = 1 WHERE id IN (${allIds})`);
    return;
  }

  try {
    const batch = filtered.map(p => ({
      latitude: p.latitude,
      longitude: p.longitude,
      altitude: p.altitude ?? undefined,
      accuracy: p.accuracy ?? undefined,
      speed: p.speed ?? undefined,
      heading: p.heading ?? undefined,
      timestamp: p.timestamp,
    }));

    await api.sendLocationsBatch(raceId, batch);
    await database.runAsync(`UPDATE locations SET synced = 1 WHERE id IN (${allIds})`);
  } catch (err) {
    console.warn('Failed to sync locations in background:', err);
  }
};

export const startBackgroundTracking = async (raceId: string, options: TrackingOptions = DEFAULT_OPTIONS) => {
  const { status: foreground } = await Location.requestForegroundPermissionsAsync();
  if (foreground !== 'granted') throw new Error('Foreground location permission denied');

  const { status: background } = await Location.requestBackgroundPermissionsAsync();
  if (background !== 'granted') throw new Error('Background location permission denied');

  setActiveRaceId(raceId);
  
  await Location.startLocationUpdatesAsync(LOCATION_TRACKING_TASK, {
    accuracy: options.accuracy,
    timeInterval: options.timeInterval,
    distanceInterval: options.distanceInterval,
    foregroundService: {
      notificationTitle: 'Race Tracking Active',
      notificationBody: 'Your location is being shared with race organizers.',
      notificationColor: '#2563EB',
    },
    pausesUpdatesAutomatically: false,
    showsBackgroundLocationIndicator: true,
  });
};

export const stopBackgroundTracking = async () => {
  const isStarted = await Location.hasStartedLocationUpdatesAsync(LOCATION_TRACKING_TASK);
  if (isStarted) {
    await Location.stopLocationUpdatesAsync(LOCATION_TRACKING_TASK);
  }
  setActiveRaceId(null);
};
