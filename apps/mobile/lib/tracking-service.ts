import * as Location from 'expo-location';
import * as BackgroundTask from 'expo-background-task';
import * as TaskManager from 'expo-task-manager';
import { openDatabaseAsync, type TrackingDatabase } from './db';
import { api } from './api';

export const LOCATION_TRACKING_TASK = 'LOCATION_TRACKING_TASK';
export const LOCATION_SYNC_TASK = 'LOCATION_SYNC_TASK';

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
  clientPointId: string | null;
  latitude: number;
  longitude: number;
  altitude: number | null;
  accuracy: number | null;
  speed: number | null;
  heading: number | null;
  timestamp: string;
  synced: number;
  syncAttempts: number;
  lastSyncError: string | null;
  updatedAt: string | null;
};

export type TrackingSessionRow = {
  raceId: string;
  startedAt: string;
  batterySaver: number;
  status: 'active' | 'stopped';
  updatedAt: string;
};

type TrackingCleanupStateRow = {
  quarantined: number;
};

// Initialize SQLite Database
let db: TrackingDatabase | null = null;

const addColumnIfMissing = async (database: TrackingDatabase, statement: string) => {
  try {
    await database.execAsync(statement);
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    if (!message.toLowerCase().includes('duplicate column')) {
      throw error;
    }
  }
};

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
      synced INTEGER DEFAULT 0,
      clientPointId TEXT,
      syncAttempts INTEGER NOT NULL DEFAULT 0,
      lastSyncError TEXT,
      updatedAt TEXT
    );
    CREATE TABLE IF NOT EXISTS tracking_sessions (
      raceId TEXT PRIMARY KEY,
      startedAt TEXT NOT NULL,
      batterySaver INTEGER NOT NULL DEFAULT 0,
      status TEXT NOT NULL DEFAULT 'active',
      updatedAt TEXT NOT NULL
    );
    CREATE INDEX IF NOT EXISTS idx_tracking_sessions_status_updated
      ON tracking_sessions(status, updatedAt);
    CREATE TABLE IF NOT EXISTS tracking_cleanup_state (
      id INTEGER PRIMARY KEY CHECK (id = 1),
      quarantined INTEGER NOT NULL DEFAULT 0,
      updatedAt TEXT NOT NULL
    );
  `);
  await addColumnIfMissing(db, 'ALTER TABLE locations ADD COLUMN clientPointId TEXT;');
  await addColumnIfMissing(db, 'ALTER TABLE locations ADD COLUMN syncAttempts INTEGER NOT NULL DEFAULT 0;');
  await addColumnIfMissing(db, 'ALTER TABLE locations ADD COLUMN lastSyncError TEXT;');
  await addColumnIfMissing(db, 'ALTER TABLE locations ADD COLUMN updatedAt TEXT;');
  await db.execAsync(`
    DELETE FROM locations
      WHERE id NOT IN (
        SELECT MIN(id)
        FROM locations
        GROUP BY raceId, timestamp
      );
    CREATE UNIQUE INDEX IF NOT EXISTS idx_locations_race_timestamp_unique
      ON locations(raceId, timestamp);
    CREATE UNIQUE INDEX IF NOT EXISTS idx_locations_client_point_id_unique
      ON locations(clientPointId)
      WHERE clientPointId IS NOT NULL;
  `);
  await db.runAsync(
    `INSERT OR IGNORE INTO tracking_cleanup_state (id, quarantined, updatedAt) VALUES (1, 0, ?)`,
    [new Date().toISOString()],
  );
  const cleanupState = await db.getFirstAsync<TrackingCleanupStateRow>(
    'SELECT quarantined FROM tracking_cleanup_state WHERE id = 1',
  );
  trackingQuarantined = cleanupState?.quarantined === 1;
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
      if (trackingQuarantined) return;
      const timestamp = new Date(location.timestamp).toISOString();
      const updatedAt = new Date().toISOString();
      await database.runAsync(
        `INSERT OR IGNORE INTO locations (
          raceId,
          clientPointId,
          latitude,
          longitude,
          altitude,
          accuracy,
          speed,
          heading,
          timestamp,
          synced,
          updatedAt
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)`,
        [
          raceId,
          createClientPointId(raceId, location),
          location.coords.latitude,
          location.coords.longitude,
          location.coords.altitude ?? null,
          location.coords.accuracy ?? null,
          location.coords.speed ?? null,
          location.coords.heading ?? null,
          timestamp,
          updatedAt,
        ]
      );
    }
    
    // Attempt to sync immediately if we have network, or wait for the sync worker
    if (trackingQuarantined) return;
    await syncPendingLocations(raceId);
  }
});

TaskManager.defineTask(LOCATION_SYNC_TASK, async () => {
  try {
    const activeSession = await getActiveTrackingSession();
    if (!activeSession) return BackgroundTask.BackgroundTaskResult.Success;

    await syncPendingLocations(activeSession.raceId);
    return BackgroundTask.BackgroundTaskResult.Success;
  } catch (error) {
    console.warn('Failed to run background location sync:', error);
    return BackgroundTask.BackgroundTaskResult.Failed;
  }
});

// Simple state management for the active race ID in the background task
let activeRaceId: string | null = null;
let trackingQuarantined = false;

export const isTrackingQuarantined = () => trackingQuarantined;

const saveActiveTrackingSession = async (raceId: string, batterySaver: boolean) => {
  const database = await initDb();
  const now = new Date().toISOString();
  await database.runAsync(
    `INSERT INTO tracking_sessions (raceId, startedAt, batterySaver, status, updatedAt)
     VALUES (?, ?, ?, 'active', ?)
     ON CONFLICT(raceId) DO UPDATE SET
       startedAt = excluded.startedAt,
       batterySaver = excluded.batterySaver,
       status = 'active',
       updatedAt = excluded.updatedAt`,
    [raceId, now, batterySaver ? 1 : 0, now]
  );
};

const markTrackingSessionStopped = async (raceId?: string | null) => {
  const database = await initDb();
  const now = new Date().toISOString();
  if (raceId) {
    await database.runAsync(
      "UPDATE tracking_sessions SET status = 'stopped', updatedAt = ? WHERE raceId = ?",
      [now, raceId]
    );
    return;
  }

  await database.runAsync(
    "UPDATE tracking_sessions SET status = 'stopped', updatedAt = ? WHERE status = 'active'",
    [now]
  );
};

export const getActiveTrackingSession = async (): Promise<TrackingSessionRow | null> => {
  const database = await initDb();
  if (trackingQuarantined) return null;
  return database.getFirstAsync<TrackingSessionRow>(
    "SELECT * FROM tracking_sessions WHERE status = 'active' ORDER BY updatedAt DESC LIMIT 1"
  );
};

export const setActiveRaceId = async (id: string | null, batterySaver = false) => {
  if (id) {
    await initDb();
    if (trackingQuarantined) {
      throw new Error('Local tracking data is quarantined until cleanup succeeds.');
    }
    await saveActiveTrackingSession(id, batterySaver);
    activeRaceId = id;
  } else {
    activeRaceId = null;
    await markTrackingSessionStopped();
  }
};

const getActiveRaceId = async () => {
  if (trackingQuarantined) return null;
  if (activeRaceId) return activeRaceId;

  const activeSession = await getActiveTrackingSession();
  activeRaceId = activeSession?.raceId ?? null;
  return activeRaceId;
};

const createClientPointId = (raceId: string, location: Location.LocationObject) => {
  return [
    raceId,
    new Date(location.timestamp).toISOString(),
    location.coords.latitude.toFixed(6),
    location.coords.longitude.toFixed(6),
  ].join(':');
};

const updateLocationRowsById = async (
  database: TrackingDatabase,
  ids: number[],
  setClause: string,
  params: (string | number | null)[] = [],
) => {
  if (ids.length === 0) return;

  const placeholders = ids.map(() => '?').join(',');
  await database.runAsync(
    `UPDATE locations SET ${setClause} WHERE id IN (${placeholders})`,
    [...params, ...ids]
  );
};

export const registerLocationSyncTask = async () => {
  const status = await BackgroundTask.getStatusAsync();
  if (status !== BackgroundTask.BackgroundTaskStatus.Available) return;

  const isRegistered = await TaskManager.isTaskRegisteredAsync(LOCATION_SYNC_TASK);
  if (isRegistered) return;

  await BackgroundTask.registerTaskAsync(LOCATION_SYNC_TASK, {
    minimumInterval: 15,
  });
};

export const unregisterLocationSyncTask = async () => {
  const isRegistered = await TaskManager.isTaskRegisteredAsync(LOCATION_SYNC_TASK);
  if (isRegistered) {
    await BackgroundTask.unregisterTaskAsync(LOCATION_SYNC_TASK);
  }
};

export const syncPendingLocations = async (raceId: string) => {
  if (trackingQuarantined) return;

  const database = await initDb();
  if (trackingQuarantined) return;
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

  const pendingIds = pending.map((point) => point.id);

  if (filtered.length === 0) {
    // These points are intentionally compacted locally as redundant, so they do not need network delivery.
    await updateLocationRowsById(database, pendingIds, 'synced = 1, updatedAt = ?', [new Date().toISOString()]);
    return;
  }

  try {
    if (trackingQuarantined) return;
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
    await updateLocationRowsById(database, pendingIds, 'synced = 1, lastSyncError = NULL, updatedAt = ?', [
      new Date().toISOString(),
    ]);
  } catch (err) {
    console.warn('Failed to sync locations in background:', err);
    const message = err instanceof Error ? err.message : String(err);
    await updateLocationRowsById(
      database,
      pendingIds,
      'syncAttempts = syncAttempts + 1, lastSyncError = ?, updatedAt = ?',
      [message, new Date().toISOString()]
    );
  }
};

/**
 * Stop native tracking and remove local tracking data when a session no longer
 * owns the device. This intentionally does not attempt to upload queued GPS
 * points after logout, refresh invalidation, or account deletion.
 */
export const clearLocalTrackingData = async (): Promise<void> => {
  const database = await initDb();
  await database.runAsync(
    `INSERT INTO tracking_cleanup_state (id, quarantined, updatedAt) VALUES (1, 1, ?)
     ON CONFLICT(id) DO UPDATE SET quarantined = 1, updatedAt = excluded.updatedAt`,
    [new Date().toISOString()],
  );
  trackingQuarantined = true;

  try {
    await stopBackgroundTracking();
  } catch (error) {
    console.warn('Failed to stop native tracking during session cleanup:', error);
  }

  try {
    await database.execAsync(`
      BEGIN TRANSACTION;
      DELETE FROM locations;
      DELETE FROM tracking_sessions;
      UPDATE tracking_cleanup_state
        SET quarantined = 0, updatedAt = datetime('now')
        WHERE id = 1;
      COMMIT;
    `);
    trackingQuarantined = false;
  } catch (error) {
    try {
      await database.execAsync('ROLLBACK;');
    } catch (rollbackError) {
      console.warn('Failed to roll back tracking cleanup:', rollbackError);
    }
    throw error;
  } finally {
    activeRaceId = null;
  }
};

export const startBackgroundTracking = async (
  raceId: string,
  options: TrackingOptions = DEFAULT_OPTIONS,
  batterySaver = false,
) => {
  await initDb();
  if (trackingQuarantined) {
    throw new Error('Local tracking data is quarantined until cleanup succeeds.');
  }

  const { status: foreground } = await Location.requestForegroundPermissionsAsync();
  if (foreground !== 'granted') throw new Error('Foreground location permission denied');

  const { status: background } = await Location.requestBackgroundPermissionsAsync();
  if (background !== 'granted') throw new Error('Background location permission denied');

  await setActiveRaceId(raceId, batterySaver);

  try {
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
  } catch (error) {
    await markTrackingSessionStopped(raceId);
    activeRaceId = null;
    throw error;
  }

  await registerLocationSyncTask().catch((error) => {
    console.warn('Failed to register background location sync:', error);
  });
};

export const stopBackgroundTracking = async () => {
  const raceId = activeRaceId;
  let failure: unknown = null;

  try {
    const isStarted = await Location.hasStartedLocationUpdatesAsync(LOCATION_TRACKING_TASK);
    if (isStarted) {
      await Location.stopLocationUpdatesAsync(LOCATION_TRACKING_TASK);
    }
  } catch (error) {
    failure = error;
  }

  await unregisterLocationSyncTask().catch((error) => {
    console.warn('Failed to unregister background location sync:', error);
  });

  try {
    await markTrackingSessionStopped(raceId);
  } catch (error) {
    failure ??= error;
  }

  activeRaceId = null;
  if (failure) throw failure;
};
