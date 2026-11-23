import type { Coordinate, MapRegion } from '@/lib/routeGeometry';

export type MapPinKind = 'checkpoint' | 'athlete';

export interface MapPin {
  id: string;
  kind: MapPinKind;
  coordinate: Coordinate;
  label?: string;
  title?: string;
  description?: string;
}

export interface MapLine {
  id: string;
  coordinates: Coordinate[];
  color: string;
  width: number;
}

export interface MapCircle {
  id: string;
  center: Coordinate;
  radius: number;
  fillColor: string;
}

export interface MapTheme {
  primary: string;
  surface: string;
  text: string;
  userLocation: string;
}

export interface RaceMapProps {
  initialRegion: MapRegion;
  lines?: MapLine[];
  pins?: MapPin[];
  circles?: MapCircle[];
  userLocation?: Coordinate | null;
  showsUserLocation?: boolean;
  followUser?: boolean;
  recenterKey?: number;
  onPinPress?: (id: string) => void;
  accessibilityLabel?: string;
  theme: MapTheme;
  height?: number;
  fill?: boolean;
}

export const NO_LINES: MapLine[] = [];
export const NO_PINS: MapPin[] = [];
export const NO_CIRCLES: MapCircle[] = [];
