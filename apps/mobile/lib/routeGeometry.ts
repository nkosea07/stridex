export type Coordinate = { latitude: number; longitude: number };

export type MapRegion = Coordinate & { latitudeDelta: number; longitudeDelta: number };

export type TrackPoint = Coordinate & { time: string | null };

const MIN_DELTA = 0.01;
const PADDING = 1.25;

export function isValidCoordinate(value: Partial<Coordinate> | null | undefined): value is Coordinate {
  return Boolean(value)
    && Number.isFinite(value!.latitude) && Number.isFinite(value!.longitude)
    && Math.abs(value!.latitude!) <= 90 && Math.abs(value!.longitude!) <= 180
    && !(value!.latitude === 0 && value!.longitude === 0);
}

export function toCoordinates<T>(items: T[], pick: (item: T) => { latitude: unknown; longitude: unknown }): Coordinate[] {
  return items
    .map((item) => {
      const raw = pick(item);
      return { latitude: Number(raw.latitude), longitude: Number(raw.longitude) };
    })
    .filter(isValidCoordinate);
}

export function regionFor(coordinates: Coordinate[]): MapRegion | null {
  const valid = coordinates.filter(isValidCoordinate);
  if (valid.length === 0) return null;
  let minLat = valid[0].latitude;
  let maxLat = valid[0].latitude;
  let minLng = valid[0].longitude;
  let maxLng = valid[0].longitude;
  for (const point of valid) {
    minLat = Math.min(minLat, point.latitude);
    maxLat = Math.max(maxLat, point.latitude);
    minLng = Math.min(minLng, point.longitude);
    maxLng = Math.max(maxLng, point.longitude);
  }
  return {
    latitude: (minLat + maxLat) / 2,
    longitude: (minLng + maxLng) / 2,
    latitudeDelta: Math.max((maxLat - minLat) * PADDING, MIN_DELTA),
    longitudeDelta: Math.max((maxLng - minLng) * PADDING, MIN_DELTA),
  };
}

export function mergeTrack(existing: TrackPoint[], incoming: TrackPoint[], maxPoints = 2000): TrackPoint[] {
  if (incoming.length === 0) return existing;
  const seen = new Set(existing.map((point) => point.time));
  const merged = [...existing];
  for (const point of incoming) {
    if (point.time && seen.has(point.time)) continue;
    if (point.time) seen.add(point.time);
    merged.push(point);
  }
  merged.sort((a, b) => (a.time ?? '').localeCompare(b.time ?? ''));
  return merged.length > maxPoints ? merged.slice(merged.length - maxPoints) : merged;
}

export function minutesSince(time: string | null | undefined, now: number = Date.now()): number | null {
  if (!time) return null;
  const parsed = Date.parse(time);
  if (Number.isNaN(parsed)) return null;
  return Math.max(0, Math.floor((now - parsed) / 60000));
}
