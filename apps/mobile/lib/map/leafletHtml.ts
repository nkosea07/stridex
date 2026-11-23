import type { Coordinate, MapRegion } from '@/lib/routeGeometry';
import type { MapConfig } from './config';
import { LEAFLET_CSS, LEAFLET_JS } from './leafletAssets.generated';
import type { LinePatch } from './scenePatch';
import type { MapCircle, MapPin } from './types';

export interface LeafletLabels {
  zoomIn: string;
  zoomOut: string;
}

export interface PinTheme {
  primary: string;
  surface: string;
}

export type LeafletInbound =
  | { type: 'lines'; patch: LinePatch }
  | { type: 'pins'; pins: MapPin[]; theme: PinTheme }
  | { type: 'circles'; circles: MapCircle[] }
  | { type: 'user'; location: Coordinate | null; follow: boolean; color: string }
  | { type: 'recenter' };

export type LeafletOutbound =
  | { type: 'ready' }
  | { type: 'pinPress'; id: string }
  | { type: 'openUrl'; url: string }
  | { type: 'error'; message: string };

const LINE_SEPARATOR = String.fromCharCode(0x2028);
const PARAGRAPH_SEPARATOR = String.fromCharCode(0x2029);

function scriptJson(value: unknown): string {
  return JSON.stringify(value)
    .split('<').join('\\u003c')
    .split(LINE_SEPARATOR).join('\\u2028')
    .split(PARAGRAPH_SEPARATOR).join('\\u2029');
}

export function injectionFor(message: LeafletInbound): string {
  return `window.__raceMap && window.__raceMap.receive(${scriptJson(message)}); true;`;
}

export function parseLeafletMessage(raw: string): LeafletOutbound | null {
  try {
    const value = JSON.parse(raw) as Partial<LeafletOutbound> & Record<string, unknown>;
    if (value.type === 'ready') return { type: 'ready' };
    if (value.type === 'pinPress' && typeof value.id === 'string') return { type: 'pinPress', id: value.id };
    if (value.type === 'openUrl' && typeof value.url === 'string' && value.url.startsWith('https://')) {
      return { type: 'openUrl', url: value.url };
    }
    if (value.type === 'error' && typeof value.message === 'string') return { type: 'error', message: value.message };
  } catch {
    return null;
  }
  return null;
}

function withoutTrailingSlash(value: string): string {
  return value.split('#')[0].replace(/\/+$/, '');
}

export function isAllowedNavigation(url: string, baseUrl: string): boolean {
  if (url === 'about:blank') return true;
  return withoutTrailingSlash(url).toLowerCase() === withoutTrailingSlash(baseUrl).toLowerCase();
}

export function buildLeafletHtml(config: MapConfig, region: MapRegion, labels: LeafletLabels): string {
  const boot = scriptJson({
    tileUrl: config.tileUrl,
    attribution: config.attribution,
    maxZoom: config.maxZoom,
    region,
    labels,
  });
  return `<!doctype html>
<html>
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no" />
<style>${LEAFLET_CSS}</style>
<style>
  html, body, #map { height: 100%; margin: 0; padding: 0; background: #e5e7eb; }
  .rt-icon { width: 0; height: 0; }
  .rt-pin { position: absolute; transform: translate(-50%, -50%); display: flex; align-items: center; justify-content: center; box-sizing: border-box; border-radius: 999px; border: 2px solid; box-shadow: 0 1px 3px rgba(0,0,0,.35); font: 700 12px -apple-system, Roboto, sans-serif; white-space: nowrap; }
  .rt-checkpoint { width: 20px; height: 20px; }
  .rt-checkpoint::after { content: ''; width: 6px; height: 6px; border-radius: 999px; background: currentColor; }
  .rt-athlete { min-width: 30px; height: 30px; padding: 0 6px; color: #fff; border-color: #fff; }
  .rt-popup { cursor: pointer; }
</style>
</head>
<body>
<div id="map" role="application"></div>
<script>${LEAFLET_JS}</script>
<script>
(function () {
  var BOOT = ${boot};
  function post(message) { if (window.ReactNativeWebView) window.ReactNativeWebView.postMessage(JSON.stringify(message)); }
  if (!window.L) { post({ type: 'error', message: 'leaflet-unavailable' }); return; }
  var map = L.map('map', { zoomControl: false, attributionControl: true, preferCanvas: true });
  L.control.zoom({ zoomInTitle: BOOT.labels.zoomIn, zoomOutTitle: BOOT.labels.zoomOut }).addTo(map);
  L.tileLayer(BOOT.tileUrl, { maxZoom: BOOT.maxZoom, attribution: BOOT.attribution, subdomains: 'abc' }).addTo(map);
  map.createPane('heat');
  map.getPane('heat').style.zIndex = 350;
  var heatRenderer = L.canvas({ pane: 'heat' });
  var circleGroup = L.layerGroup().addTo(map);
  var lineLayers = {};
  var pinLayers = {};
  var pinKeys = {};
  var userDot = null;
  var user = null;
  var followPaused = false;
  map.on('dragstart', function () { followPaused = true; });
  function latLng(point) { return [point.latitude, point.longitude]; }
  function bounds(region) {
    return [[region.latitude - region.latitudeDelta / 2, region.longitude - region.longitudeDelta / 2],
            [region.latitude + region.latitudeDelta / 2, region.longitude + region.longitudeDelta / 2]];
  }
  function pinIcon(pin, theme) {
    var el = document.createElement('div');
    if (pin.kind === 'athlete') {
      el.className = 'rt-pin rt-athlete';
      el.style.background = theme.primary;
      el.textContent = pin.label || '\\u2022';
    } else {
      el.className = 'rt-pin rt-checkpoint';
      el.style.background = theme.surface;
      el.style.borderColor = theme.primary;
      el.style.color = theme.primary;
    }
    return L.divIcon({ className: 'rt-icon', html: el.outerHTML, iconSize: null, iconAnchor: [0, 0] });
  }
  function popupContent(pin) {
    if (!pin.title && !pin.description) return null;
    var box = document.createElement('div');
    box.className = 'rt-popup';
    box.setAttribute('role', 'button');
    if (pin.title) { var strong = document.createElement('strong'); strong.textContent = pin.title; box.appendChild(strong); }
    if (pin.description) { var detail = document.createElement('div'); detail.textContent = pin.description; box.appendChild(detail); }
    box.addEventListener('click', function () { post({ type: 'pinPress', id: pin.id }); });
    return box;
  }
  function setLines(patch) {
    patch.remove.forEach(function (id) {
      if (lineLayers[id]) { map.removeLayer(lineLayers[id]); delete lineLayers[id]; }
    });
    patch.set.forEach(function (line) {
      var points = line.coordinates.map(latLng);
      var style = { color: line.color, weight: line.width };
      if (lineLayers[line.id]) { lineLayers[line.id].setLatLngs(points); lineLayers[line.id].setStyle(style); }
      else { lineLayers[line.id] = L.polyline(points, { color: line.color, weight: line.width, interactive: false }).addTo(map); }
    });
    patch.append.forEach(function (extra) {
      var layer = lineLayers[extra.id];
      if (layer) extra.coordinates.forEach(function (point) { layer.addLatLng(latLng(point)); });
    });
  }
  function setPins(pins, theme) {
    var seen = {};
    pins.forEach(function (pin) {
      seen[pin.id] = true;
      var key = JSON.stringify([pin.kind, pin.label || '', pin.title || '', pin.description || '', theme.primary, theme.surface]);
      var marker = pinLayers[pin.id];
      if (!marker) {
        marker = L.marker(latLng(pin.coordinate), { keyboard: true, title: pin.title || '', alt: pin.title || '' });
        marker.on('click', function () { if (!marker.getPopup()) post({ type: 'pinPress', id: pin.id }); });
        marker.addTo(map);
        pinLayers[pin.id] = marker;
      } else {
        marker.setLatLng(latLng(pin.coordinate));
      }
      if (pinKeys[pin.id] !== key) {
        marker.setIcon(pinIcon(pin, theme));
        marker.unbindPopup();
        var content = popupContent(pin);
        if (content) marker.bindPopup(content, { closeButton: false });
        pinKeys[pin.id] = key;
      }
    });
    Object.keys(pinLayers).forEach(function (id) {
      if (!seen[id]) { map.removeLayer(pinLayers[id]); delete pinLayers[id]; delete pinKeys[id]; }
    });
  }
  function setCircles(circles) {
    circleGroup.clearLayers();
    circles.forEach(function (circle) {
      L.circle(latLng(circle.center), { radius: circle.radius, stroke: false, fillColor: circle.fillColor, fillOpacity: 1, interactive: false, pane: 'heat', renderer: heatRenderer }).addTo(circleGroup);
    });
  }
  function setUser(location, follow, color) {
    user = location;
    if (!location) {
      if (userDot) { map.removeLayer(userDot); userDot = null; }
      return;
    }
    if (!userDot) {
      userDot = L.circleMarker(latLng(location), { radius: 8, color: '#ffffff', weight: 3, fillColor: color, fillOpacity: 1, interactive: false }).addTo(map);
    } else {
      userDot.setLatLng(latLng(location));
      userDot.setStyle({ fillColor: color });
    }
    if (follow && !followPaused) map.panTo(latLng(location), { animate: true });
  }
  function recenter() {
    followPaused = false;
    if (user) map.setView(latLng(user), Math.max(map.getZoom() || 0, 16), { animate: true });
  }
  function receive(message) {
    if (!message || typeof message !== 'object') return;
    if (message.type === 'lines') setLines(message.patch);
    else if (message.type === 'pins') setPins(message.pins, message.theme);
    else if (message.type === 'circles') setCircles(message.circles);
    else if (message.type === 'user') setUser(message.location, message.follow, message.color);
    else if (message.type === 'recenter') recenter();
  }
  document.addEventListener('click', function (event) {
    var link = event.target && event.target.closest ? event.target.closest('a[href]') : null;
    if (!link) return;
    var href = link.getAttribute('href') || '';
    if (href.indexOf('https://') !== 0) return;
    event.preventDefault();
    post({ type: 'openUrl', url: href });
  }, true);
  map.fitBounds(bounds(BOOT.region));
  window.__raceMap = { receive: receive };
  post({ type: 'ready' });
})();
</script>
</body>
</html>`;
}
