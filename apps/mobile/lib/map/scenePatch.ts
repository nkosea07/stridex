import type { Coordinate } from '@/lib/routeGeometry';
import type { MapLine } from './types';

export interface LinePatch {
  set: MapLine[];
  append: { id: string; coordinates: Coordinate[] }[];
  remove: string[];
}

export type LineState = Map<string, MapLine>;

function samePoint(a: Coordinate | undefined, b: Coordinate | undefined): boolean {
  return Boolean(a && b) && a!.latitude === b!.latitude && a!.longitude === b!.longitude;
}

function samePoints(a: Coordinate[], b: Coordinate[]): boolean {
  if (a === b) return true;
  if (a.length !== b.length) return false;
  for (let index = 0; index < a.length; index += 1) {
    if (!samePoint(a[index], b[index])) return false;
  }
  return true;
}

function isAppendOf(previous: Coordinate[], next: Coordinate[]): boolean {
  return previous.length > 0
    && next.length > previous.length
    && samePoint(previous[0], next[0])
    && samePoint(previous[previous.length - 1], next[previous.length - 1]);
}

export function diffLines(previous: LineState, next: MapLine[]): { patch: LinePatch; state: LineState } {
  const patch: LinePatch = { set: [], append: [], remove: [] };
  const state: LineState = new Map();
  for (const line of next) {
    state.set(line.id, line);
    const before = previous.get(line.id);
    if (!before || before.color !== line.color || before.width !== line.width) {
      patch.set.push(line);
    } else if (isAppendOf(before.coordinates, line.coordinates)) {
      patch.append.push({ id: line.id, coordinates: line.coordinates.slice(before.coordinates.length) });
    } else if (!samePoints(before.coordinates, line.coordinates)) {
      patch.set.push(line);
    }
  }
  for (const id of previous.keys()) {
    if (!state.has(id)) patch.remove.push(id);
  }
  return { patch, state };
}

export function isEmptyPatch(patch: LinePatch): boolean {
  return patch.set.length === 0 && patch.append.length === 0 && patch.remove.length === 0;
}
