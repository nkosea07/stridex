import { mapConfig } from '@/lib/map/config';
import type { RaceMapProps } from '@/lib/map/types';
import { LeafletRaceMap } from './LeafletRaceMap';
import { NativeRaceMap } from './NativeRaceMap';

export type { MapCircle, MapLine, MapPin, MapTheme, RaceMapProps } from '@/lib/map/types';
export { useMapTheme } from './useMapTheme';

export function RaceMap(props: RaceMapProps) {
  return mapConfig.provider === 'leaflet' ? <LeafletRaceMap {...props} /> : <NativeRaceMap {...props} />;
}
