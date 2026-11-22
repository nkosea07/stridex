import { isValidCoordinate, type Coordinate, type TrackPoint } from './routeGeometry';
import type { FavoriteAthlete, HeatCell } from '@/types';

export const HEAT_WINDOW_MINUTES = 10;
export const MAX_HEAT_CELLS = 300;
export const MAX_FOLLOWED_ON_MAP = 10;
export const HEAT_CELL_RADIUS_METERS = 70;
export const RACE_MAP_REFRESH_MS = 30000;

export function heatFill(weight: number, maxWeight: number): string {
  const ratio = maxWeight > 0 ? Math.min(Math.max(weight / maxWeight, 0), 1) : 0;
  const alpha = 0.15 + 0.45 * Math.sqrt(ratio);
  return `rgba(234, 88, 12, ${alpha.toFixed(2)})`;
}

export function maxHeatWeight(cells: HeatCell[]): number {
  return cells.reduce((max, cell) => Math.max(max, cell.weight), 0);
}

export function latestPosition(points: TrackPoint[]): Coordinate | null {
  for (let index = points.length - 1; index >= 0; index -= 1) {
    const { latitude, longitude } = points[index];
    if (isValidCoordinate({ latitude, longitude })) return { latitude, longitude };
  }
  return null;
}

export function followedInRace(favorites: FavoriteAthlete[], raceId: string, limit = MAX_FOLLOWED_ON_MAP): FavoriteAthlete[] {
  const seen = new Set<string>();
  return favorites
    .filter((row) => row.raceId === raceId && row.participantId && !seen.has(row.participantId) && seen.add(row.participantId))
    .slice(0, limit);
}

export function heatWindowStart(now: number = Date.now()): Date {
  return new Date(now - HEAT_WINDOW_MINUTES * 60 * 1000);
}
