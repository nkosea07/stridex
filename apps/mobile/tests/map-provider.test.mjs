/* eslint-disable import/no-unresolved -- executed through the web workspace's Vitest install */
import { createHash } from 'node:crypto'
import { createRequire } from 'node:module'
import { afterEach, describe, expect, it, vi } from 'vitest'

vi.mock('expo-constants', () => ({ default: { expoConfig: { extra: {} } } }))

const require = createRequire(import.meta.url)
const appConfig = require('../app.config.js')
const region = { latitude: -17.83, longitude: 31.05, latitudeDelta: 0.05, longitudeDelta: 0.05 }
const labels = { zoomIn: 'Sondeza', zoomOut: 'Hlehlisa' }
const theme = { primary: '#0F766E', surface: '#ffffff' }
const base = { android: { package: 'com.racetracker.app' }, ios: { bundleIdentifier: 'com.racetracker.app' } }

function bootPage(html) {
  const script = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map((match) => match[1]).at(-1)
  const posted = []
  const lines = {}
  const markers = []
  const counts = { circles: 0, userDots: 0, panTo: 0, setView: 0, fit: 0 }
  let documentClick = null
  let zoomOptions = null
  const layer = (extra = {}) => ({ addTo() { return this }, ...extra })
  const L = {
    map: () => ({
      fitBounds() { counts.fit += 1 }, setView() { counts.setView += 1 }, panTo() { counts.panTo += 1 }, getZoom: () => 12,
      on() {}, createPane() {}, getPane: () => ({ style: {} }), removeLayer(target) { target.removed = true },
    }),
    control: { zoom: (options) => { zoomOptions = options; return layer() } },
    tileLayer: () => layer(),
    canvas: () => ({}),
    layerGroup: () => layer({ clearLayers() {} }),
    polyline: (points) => {
      const line = layer({ points: [...points], setLatLngs(next) { this.points = [...next] }, setStyle() {}, addLatLng(point) { this.points.push(point) } })
      return line
    },
    circle: () => { counts.circles += 1; return layer() },
    circleMarker: () => { counts.userDots += 1; return layer({ setLatLng() {}, setStyle() {} }) },
    marker: (position) => {
      const handlers = {}
      const marker = layer({ position, icon: null, popup: null, handlers,
        on(event, fn) { handlers[event] = fn; return this }, setLatLng(next) { this.position = next }, setIcon(icon) { this.icon = icon },
        unbindPopup() { this.popup = null }, bindPopup(content) { this.popup = content }, getPopup() { return this.popup } })
      markers.push(marker)
      return marker
    },
    divIcon: (options) => options,
  }
  const element = () => {
    const listeners = {}
    return {
      style: {}, children: [], className: '', attributes: {},
      appendChild(child) { this.children.push(child) }, setAttribute(name, value) { this.attributes[name] = value },
      addEventListener(event, fn) { listeners[event] = fn }, click() { listeners.click?.() },
      set textContent(value) { this.text = value }, get textContent() { return this.text },
      get outerHTML() { return `<div class="${this.className}">${String(this.text ?? '').replace(/</g, '&lt;')}</div>` },
    }
  }
  const window = { ReactNativeWebView: { postMessage: (message) => posted.push(JSON.parse(message)) } }
  const document = { createElement: element, addEventListener: (event, fn) => { if (event === 'click') documentClick = fn } }
  window.L = L
  new Function('window', 'document', 'L', script)(window, document, L)
  const send = (code) => new Function('window', code)(window)
  return { posted, lines, markers, counts, send, zoomOptions, click: (target) => documentClick(target), window }
}

afterEach(() => vi.restoreAllMocks())

describe('bundled Leaflet', () => {
  it('ships the published Leaflet 1.9.4 files, byte for byte', async () => {
    const assets = await import('../lib/map/leafletAssets.generated.ts')
    const digest = (value) => `sha256-${createHash('sha256').update(value, 'utf8').digest('base64')}`

    expect(assets.LEAFLET_VERSION).toBe('1.9.4')
    expect(digest(assets.LEAFLET_JS)).toBe('sha256-20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo=')
    expect(digest(assets.LEAFLET_CSS)).toBe('sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY=')
  })

  it('inlines Leaflet instead of loading it from a CDN', async () => {
    const { buildLeafletHtml } = await import('../lib/map/leafletHtml.ts')
    const { resolveMapConfig } = await import('../lib/map/config.ts')

    const html = buildLeafletHtml(resolveMapConfig(undefined), region, labels)

    expect(html).not.toMatch(/<script[^>]+src=/)
    expect(html).not.toMatch(/<link[^>]+stylesheet/)
    expect(html).toContain('https://tile.openstreetmap.org/{z}/{x}/{y}.png')
  })
})

describe('Leaflet page behaviour', () => {
  async function page() {
    const { buildLeafletHtml, injectionFor } = await import('../lib/map/leafletHtml.ts')
    const { resolveMapConfig } = await import('../lib/map/config.ts')
    const booted = bootPage(buildLeafletHtml(resolveMapConfig(undefined), region, labels))
    return { ...booted, inject: (message) => booted.send(injectionFor(message)) }
  }

  it('starts ready with translated zoom controls', async () => {
    const map = await page()

    expect(map.posted).toEqual([{ type: 'ready' }])
    expect(map.zoomOptions).toEqual({ zoomInTitle: 'Sondeza', zoomOutTitle: 'Hlehlisa' })
    expect(map.counts.fit).toBe(1)
  })

  it('leaves Leaflet controls alone and opens only real https links outside the app', async () => {
    const map = await page()
    const event = (href) => {
      const link = { getAttribute: () => href }
      return { target: { closest: () => link }, preventDefault: vi.fn() }
    }

    const zoom = event('#')
    map.click(zoom)
    const attribution = event('https://www.openstreetmap.org/copyright')
    map.click(attribution)

    expect(zoom.preventDefault).not.toHaveBeenCalled()
    expect(attribution.preventDefault).toHaveBeenCalled()
    expect(map.posted.filter((message) => message.type === 'openUrl')).toEqual([{ type: 'openUrl', url: 'https://www.openstreetmap.org/copyright' }])
  })

  it('updates pins in place and reports taps from popups or bare pins', async () => {
    const map = await page()
    const athlete = { id: 'athlete:1', kind: 'athlete', coordinate: region, label: '101', title: 'Ada', description: 'CP2' }
    const checkpoint = { id: 'checkpoint:9', kind: 'checkpoint', coordinate: region }

    map.inject({ type: 'pins', pins: [athlete, checkpoint], theme })
    map.inject({ type: 'pins', pins: [{ ...athlete, coordinate: { ...region, latitude: -17.9 } }, checkpoint], theme })

    expect(map.markers).toHaveLength(2)
    expect(map.markers[0].position).toEqual([-17.9, 31.05])
    map.markers[0].popup.click()
    map.markers[1].handlers.click()
    map.markers[0].handlers.click()
    expect(map.posted.filter((message) => message.type === 'pinPress').map((message) => message.id)).toEqual(['athlete:1', 'checkpoint:9'])

    map.inject({ type: 'pins', pins: [checkpoint], theme })
    expect(map.markers[0].removed).toBe(true)
  })

  it('follows by panning without changing zoom, and recenters on request', async () => {
    const map = await page()

    map.inject({ type: 'user', location: region, follow: true, color: '#2563EB' })
    map.inject({ type: 'user', location: { ...region, latitude: -17.84 }, follow: true, color: '#2563EB' })
    map.inject({ type: 'user', location: region, follow: false, color: '#2563EB' })
    map.inject({ type: 'recenter' })

    expect(map.counts).toMatchObject({ userDots: 1, panTo: 2, setView: 1 })
  })
})

describe('scene diffing', () => {
  const a = { latitude: 1, longitude: 1 }
  const b = { latitude: 2, longitude: 2 }
  const c = { latitude: 3, longitude: 3 }

  it('sends new lines, only appended track points, and nothing when unchanged', async () => {
    const { diffLines, isEmptyPatch } = await import('../lib/map/scenePatch.ts')
    const route = { id: 'route', coordinates: [a, b], color: '#999', width: 4 }

    const first = diffLines(new Map(), [route, { id: 'track', coordinates: [a], color: '#0F766E', width: 3 }])
    const second = diffLines(first.state, [{ ...route, coordinates: [a, b] }, { id: 'track', coordinates: [a, b, c], color: '#0F766E', width: 3 }])
    const third = diffLines(second.state, [route, { id: 'track', coordinates: [a, b, c], color: '#0F766E', width: 3 }])

    expect(first.patch.set.map((line) => line.id)).toEqual(['route', 'track'])
    expect(second.patch).toEqual({ set: [], append: [{ id: 'track', coordinates: [b, c] }], remove: [] })
    expect(isEmptyPatch(third.patch)).toBe(true)
  })

  it('replaces rewritten lines and removes missing ones', async () => {
    const { diffLines } = await import('../lib/map/scenePatch.ts')
    const start = diffLines(new Map(), [{ id: 'track', coordinates: [a, b], color: '#0F766E', width: 3 }, { id: 'route', coordinates: [a], color: '#999', width: 4 }])

    const next = diffLines(start.state, [{ id: 'track', coordinates: [b, c, a], color: '#0F766E', width: 3 }])

    expect(next.patch.set.map((line) => line.id)).toEqual(['track'])
    expect(next.patch.remove).toEqual(['route'])
  })
})

describe('WebView safety', () => {
  it('allows only the map document itself, with or without a trailing slash', async () => {
    const { isAllowedNavigation } = await import('../lib/map/leafletHtml.ts')

    expect(isAllowedNavigation('https://race.example.com/', 'https://race.example.com')).toBe(true)
    expect(isAllowedNavigation('https://race.example.com', 'https://race.example.com/')).toBe(true)
    expect(isAllowedNavigation('about:blank', 'https://race.example.com')).toBe(true)
    expect(isAllowedNavigation('https://race.example.com/admin', 'https://race.example.com')).toBe(false)
    expect(isAllowedNavigation('https://evil.example/', 'https://race.example.com')).toBe(false)
  })

  it('cannot be broken out of the script tag by configuration or scene data', async () => {
    const { buildLeafletHtml, injectionFor } = await import('../lib/map/leafletHtml.ts')
    const hostile = 'x</script><script>alert(1)</script>'

    const html = buildLeafletHtml({ provider: 'leaflet', tileUrl: 'https://t/{z}/{x}/{y}.png', attribution: hostile, maxZoom: 19 }, region, labels)
    const injected = injectionFor({ type: 'pins', pins: [{ id: 'a', kind: 'athlete', coordinate: region, label: hostile }], theme })

    expect(html).not.toContain('<script>alert(1)')
    expect(injected).not.toContain('</script>')
  })

  it('accepts only known messages and https links from the WebView', async () => {
    const { parseLeafletMessage } = await import('../lib/map/leafletHtml.ts')

    expect(parseLeafletMessage('{"type":"ready"}')).toEqual({ type: 'ready' })
    expect(parseLeafletMessage('{"type":"pinPress","id":"athlete:1"}')).toEqual({ type: 'pinPress', id: 'athlete:1' })
    expect(parseLeafletMessage('{"type":"openUrl","url":"javascript:alert(1)"}')).toBeNull()
    expect(parseLeafletMessage('{"type":"pinPress","id":7}')).toBeNull()
    expect(parseLeafletMessage('not json')).toBeNull()
  })
})

describe('map settings', () => {
  it('builds Leaflet on OpenStreetMap by default and passes the settings to the app', async () => {
    const { resolveMapConfig, DEFAULT_TILE_URL, DEFAULT_TILE_ATTRIBUTION, DEFAULT_MAX_ZOOM } = await import('../lib/map/config.ts')

    const built = appConfig.withMaps(base, {})

    expect(built.extra.map).toEqual({ provider: 'leaflet', tileUrl: DEFAULT_TILE_URL, attribution: DEFAULT_TILE_ATTRIBUTION, maxZoom: DEFAULT_MAX_ZOOM })
    expect(appConfig.MAP_DEFAULTS).toEqual({ tileUrl: DEFAULT_TILE_URL, attribution: DEFAULT_TILE_ATTRIBUTION, maxZoom: DEFAULT_MAX_ZOOM })
    expect(resolveMapConfig(built.extra.map)).toEqual(built.extra.map)
    expect(built.android.config).toBeUndefined()
  })

  it('accepts any https tile server but requires its attribution and a valid max zoom', () => {
    const carto = 'https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}.png'

    expect(appConfig.withMaps(base, { EXPO_PUBLIC_MAP_TILE_URL: carto, EXPO_PUBLIC_MAP_TILE_ATTRIBUTION: '© CARTO', EXPO_PUBLIC_MAP_MAX_ZOOM: '20' }).extra.map)
      .toMatchObject({ tileUrl: carto, attribution: '© CARTO', maxZoom: 20 })
    expect(() => appConfig.withMaps(base, { EXPO_PUBLIC_MAP_TILE_URL: carto })).toThrow(/ATTRIBUTION is required/)
    expect(() => appConfig.withMaps(base, { EXPO_PUBLIC_MAP_TILE_URL: 'http://t/{z}/{x}/{y}.png', EXPO_PUBLIC_MAP_TILE_ATTRIBUTION: 'x' })).toThrow(/https URL template/)
    expect(() => appConfig.withMaps(base, { EXPO_PUBLIC_MAP_MAX_ZOOM: '25' })).toThrow(/MAX_ZOOM/)
    expect(() => appConfig.withMaps(base, { EXPO_PUBLIC_MAP_MAX_ZOOM: 'high' })).toThrow(/MAX_ZOOM/)
    expect(() => appConfig.withMaps(base, { EXPO_PUBLIC_MAP_PROVIDER: 'bing' })).toThrow(/must be one of/)
  })

  it('writes Google keys only when Google Maps is used', () => {
    const native = appConfig.withMaps(base, { EXPO_PUBLIC_MAP_PROVIDER: 'native', GOOGLE_MAPS_ANDROID_API_KEY: 'android-key' })
    const google = appConfig.withMaps(base, { EXPO_PUBLIC_MAP_PROVIDER: 'google', GOOGLE_MAPS_ANDROID_API_KEY: 'android-key', GOOGLE_MAPS_IOS_API_KEY: 'ios-key' })

    expect(native.android.config.googleMaps.apiKey).toBe('android-key')
    expect(native.ios.config).toBeUndefined()
    expect(google.ios.config.googleMapsApiKey).toBe('ios-key')
  })

  it('fails EAS builds on a missing Google key but only warns for local config evaluation', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})

    expect(() => appConfig.withMaps(base, { EXPO_PUBLIC_MAP_PROVIDER: 'native', EAS_BUILD: 'true' })).toThrow(/GOOGLE_MAPS_ANDROID_API_KEY/)
    expect(() => appConfig.withMaps(base, { EXPO_PUBLIC_MAP_PROVIDER: 'google', GOOGLE_MAPS_ANDROID_API_KEY: 'a', EAS_BUILD: 'true' })).toThrow(/GOOGLE_MAPS_IOS_API_KEY/)
    expect(appConfig.withMaps(base, { EXPO_PUBLIC_MAP_PROVIDER: 'native' }).extra.map.provider).toBe('native')
    expect(warn).toHaveBeenCalledWith(expect.stringMatching(/GOOGLE_MAPS_ANDROID_API_KEY/))
  })

  it('falls back safely when the runtime settings are missing or malformed', async () => {
    const { resolveMapConfig, DEFAULT_TILE_URL, DEFAULT_TILE_ATTRIBUTION } = await import('../lib/map/config.ts')

    expect(resolveMapConfig(undefined).provider).toBe('leaflet')
    expect(resolveMapConfig({ provider: 'bing', tileUrl: 'https://t/{z}/{x}/{y}.png' })).toMatchObject({ provider: 'leaflet', tileUrl: DEFAULT_TILE_URL, attribution: DEFAULT_TILE_ATTRIBUTION })
    expect(resolveMapConfig({ provider: 'google', maxZoom: 30 })).toMatchObject({ provider: 'google', maxZoom: 19 })
  })
})
