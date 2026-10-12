// Central map configuration. Maps are rendered with react-map-gl (Mapbox engine); a Mapbox
// access token is required. Without one, map views intentionally render nothing rather than a
// broken or misleading fallback.

const DEFAULT_MAPBOX_STYLE = 'mapbox://styles/mapbox/dark-v11'

export function getMapAccessToken(): string {
  return (import.meta.env.VITE_MAPBOX_TOKEN as string | undefined) ?? ''
}

export function isMapConfigured(): boolean {
  return getMapAccessToken().length > 0
}

export function resolveMapStyleUrl(): string {
  return (import.meta.env.VITE_MAPBOX_STYLE_URL as string | undefined)?.trim() || DEFAULT_MAPBOX_STYLE
}
