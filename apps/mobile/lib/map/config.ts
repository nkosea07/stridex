import Constants from 'expo-constants';

export type MapProvider = 'leaflet' | 'native' | 'google';

export const MAP_PROVIDERS: readonly MapProvider[] = ['leaflet', 'native', 'google'];

export const DEFAULT_TILE_URL = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
export const DEFAULT_TILE_ATTRIBUTION =
  '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';
export const DEFAULT_MAX_ZOOM = 19;

export interface MapConfig {
  provider: MapProvider;
  tileUrl: string;
  attribution: string;
  maxZoom: number;
}

const DEFAULT_CONFIG: MapConfig = {
  provider: 'leaflet',
  tileUrl: DEFAULT_TILE_URL,
  attribution: DEFAULT_TILE_ATTRIBUTION,
  maxZoom: DEFAULT_MAX_ZOOM,
};

function isTileUrl(value: unknown): value is string {
  return typeof value === 'string' && value.startsWith('https://') && ['{z}', '{x}', '{y}'].every((token) => value.includes(token));
}

export function resolveMapConfig(raw: unknown): MapConfig {
  if (!raw || typeof raw !== 'object') return DEFAULT_CONFIG;
  const value = raw as Record<string, unknown>;
  const provider = MAP_PROVIDERS.includes(value.provider as MapProvider) ? value.provider as MapProvider : 'leaflet';
  const customTiles = isTileUrl(value.tileUrl) && value.tileUrl !== DEFAULT_TILE_URL
    && typeof value.attribution === 'string' && value.attribution.trim().length > 0;
  const maxZoom = typeof value.maxZoom === 'number' && Number.isInteger(value.maxZoom) && value.maxZoom >= 1 && value.maxZoom <= 22
    ? value.maxZoom
    : DEFAULT_MAX_ZOOM;
  return {
    provider,
    tileUrl: customTiles ? value.tileUrl as string : DEFAULT_TILE_URL,
    attribution: customTiles ? value.attribution as string : DEFAULT_TILE_ATTRIBUTION,
    maxZoom,
  };
}

export const mapConfig: MapConfig = resolveMapConfig(Constants.expoConfig?.extra?.map);
