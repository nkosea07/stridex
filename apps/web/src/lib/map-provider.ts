// Central map configuration so components stay provider-agnostic. Set VITE_MAP_PROVIDER=maplibre
// to fall back to an open style when Mapbox is unavailable (e.g., no token).

export type MapProvider = 'mapbox' | 'maplibre'

const DEFAULT_MAPBOX_STYLE = 'mapbox://styles/mapbox/dark-v11'
const DEFAULT_MAPLIBRE_STYLE = 'https://demotiles.maplibre.org/style.json'

export function getMapProvider(): MapProvider {
  const configured = (import.meta.env.VITE_MAP_PROVIDER as string | undefined)?.trim().toLowerCase()
  if (configured === 'maplibre') {
    return 'maplibre'
  }
  return 'mapbox'
}

export function getMapAccessToken(): string {
  return (import.meta.env.VITE_MAPBOX_TOKEN as string | undefined) ?? ''
}

export function isMapConfigured(): boolean {
  return getMapProvider() === 'maplibre' || getMapAccessToken().length > 0
}

export function resolveMapStyleUrl(): string {
  if (getMapProvider() === 'maplibre' || !isMapConfigured()) {
    return (import.meta.env.VITE_MAPLIBRE_STYLE_URL as string | undefined)?.trim() || DEFAULT_MAPLIBRE_STYLE
  }
  return (import.meta.env.VITE_MAPBOX_STYLE_URL as string | undefined)?.trim() || DEFAULT_MAPBOX_STYLE
}
