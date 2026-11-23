const fs = require('node:fs');
const path = require('node:path');

const APP_LINK_PATHS = ['/verify-email', '/reset-password'];

function appLinkHost(webUrl) {
  if (!webUrl) return null;
  let parsed;
  try {
    parsed = new URL(webUrl);
  } catch {
    throw new Error(`EXPO_PUBLIC_WEB_URL must be an absolute URL, got "${webUrl}"`);
  }
  if (parsed.protocol !== 'https:') return null;
  return parsed.hostname;
}

function withAppLinks(config, webUrl) {
  const host = appLinkHost(webUrl);
  if (!host) return config;
  return {
    ...config,
    ios: {
      ...config.ios,
      associatedDomains: [...new Set([...(config.ios?.associatedDomains ?? []), `applinks:${host}`])],
    },
    android: {
      ...config.android,
      intentFilters: [
        ...(config.android?.intentFilters ?? []),
        {
          action: 'VIEW',
          autoVerify: true,
          data: APP_LINK_PATHS.map((pathPrefix) => ({ scheme: 'https', host, pathPrefix })),
          category: ['BROWSABLE', 'DEFAULT'],
        },
      ],
    },
  };
}

const MAP_PROVIDERS = ['leaflet', 'native', 'google'];
const DEFAULT_TILE_URL = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
const DEFAULT_TILE_ATTRIBUTION = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';
const DEFAULT_MAX_ZOOM = 19;

function resolveMapSettings(env) {
  const provider = (env.EXPO_PUBLIC_MAP_PROVIDER || 'leaflet').trim().toLowerCase();
  if (!MAP_PROVIDERS.includes(provider)) {
    throw new Error(`EXPO_PUBLIC_MAP_PROVIDER must be one of ${MAP_PROVIDERS.join(', ')}, got "${provider}"`);
  }
  const tileUrl = (env.EXPO_PUBLIC_MAP_TILE_URL || '').trim();
  const attribution = (env.EXPO_PUBLIC_MAP_TILE_ATTRIBUTION || '').trim();
  if (tileUrl && !(tileUrl.startsWith('https://') && ['{z}', '{x}', '{y}'].every((token) => tileUrl.includes(token)))) {
    throw new Error('EXPO_PUBLIC_MAP_TILE_URL must be an https URL template containing {z}, {x} and {y}');
  }
  if (tileUrl && !attribution) {
    throw new Error('EXPO_PUBLIC_MAP_TILE_ATTRIBUTION is required with a custom EXPO_PUBLIC_MAP_TILE_URL (tile providers require credit)');
  }
  const maxZoomText = (env.EXPO_PUBLIC_MAP_MAX_ZOOM || '').trim();
  const maxZoom = maxZoomText ? Number(maxZoomText) : DEFAULT_MAX_ZOOM;
  if (!Number.isInteger(maxZoom) || maxZoom < 1 || maxZoom > 22) {
    throw new Error(`EXPO_PUBLIC_MAP_MAX_ZOOM must be a whole number from 1 to 22, got "${maxZoomText}"`);
  }
  return {
    provider,
    tileUrl: tileUrl || DEFAULT_TILE_URL,
    attribution: tileUrl ? attribution : DEFAULT_TILE_ATTRIBUTION,
    maxZoom,
  };
}

function missingKey(env, message) {
  if (env.EAS_BUILD === 'true') throw new Error(message);
  console.warn(`[maps] ${message}. Native maps will not render until it is set.`);
}

function withMaps(config, env) {
  const map = resolveMapSettings(env);
  const withSettings = { ...config, extra: { ...config.extra, map } };
  if (map.provider === 'leaflet') return withSettings;
  const androidKey = env.GOOGLE_MAPS_ANDROID_API_KEY;
  const iosKey = env.GOOGLE_MAPS_IOS_API_KEY;
  if (!androidKey) missingKey(env, `EXPO_PUBLIC_MAP_PROVIDER=${map.provider} uses Google Maps on Android and needs GOOGLE_MAPS_ANDROID_API_KEY`);
  if (map.provider === 'google' && !iosKey) missingKey(env, 'EXPO_PUBLIC_MAP_PROVIDER=google uses Google Maps on iOS and needs GOOGLE_MAPS_IOS_API_KEY');
  return {
    ...withSettings,
    android: androidKey
      ? { ...config.android, config: { ...config.android?.config, googleMaps: { apiKey: androidKey } } }
      : config.android,
    ios: map.provider === 'google' && iosKey
      ? { ...config.ios, config: { ...config.ios?.config, googleMapsApiKey: iosKey } }
      : config.ios,
  };
}

const STORE_PROFILES = new Set(['production']);
const RELEASE_PROFILES = new Set(['production', 'preview']);
const STORE_URL_VARIABLES = ['EXPO_PUBLIC_API_URL', 'EXPO_PUBLIC_WEB_URL'];

function isHttpsUrl(value) {
  try {
    return new URL(value).protocol === 'https:';
  } catch {
    return false;
  }
}

function withReleaseSettings(config, env) {
  const profile = env.EAS_BUILD_PROFILE;
  if (env.EAS_BUILD === 'true' && STORE_PROFILES.has(profile)) {
    const invalid = STORE_URL_VARIABLES.filter((name) => !isHttpsUrl((env[name] || '').trim()));
    if (invalid.length > 0) {
      throw new Error(`${invalid.join(' and ')} must be set to https URLs for "${profile}" builds`);
    }
  }
  if (!RELEASE_PROFILES.has(profile)) return config;
  return {
    ...config,
    plugins: (config.plugins ?? []).map((plugin) => {
      const [name, options] = Array.isArray(plugin) ? plugin : [plugin, undefined];
      return name === 'expo-notifications' ? [name, { ...options, mode: 'production' }] : plugin;
    }),
  };
}

const LOCAL_GOOGLE_SERVICES_FILE = './google-services.json';

function withAndroidPushCredentials(config, env, fileExists) {
  const configured = (env.GOOGLE_SERVICES_JSON || '').trim();
  const file = configured || (fileExists(LOCAL_GOOGLE_SERVICES_FILE) ? LOCAL_GOOGLE_SERVICES_FILE : '');
  if (!file) {
    if (env.EAS_BUILD === 'true' && STORE_PROFILES.has(env.EAS_BUILD_PROFILE)) {
      throw new Error(`GOOGLE_SERVICES_JSON must point to the Firebase google-services.json for "${env.EAS_BUILD_PROFILE}" builds so Android devices can receive push notifications`);
    }
    return config;
  }
  return { ...config, android: { ...config.android, googleServicesFile: file } };
}

module.exports = ({ config, projectRoot }) => withAndroidPushCredentials(
  withReleaseSettings(withMaps(withAppLinks(config, process.env.EXPO_PUBLIC_WEB_URL), process.env), process.env),
  process.env,
  (file) => fs.existsSync(path.resolve(projectRoot ?? process.cwd(), file)),
);
module.exports.withReleaseSettings = withReleaseSettings;
module.exports.withAndroidPushCredentials = withAndroidPushCredentials;
module.exports.withAppLinks = withAppLinks;
module.exports.APP_LINK_PATHS = APP_LINK_PATHS;
module.exports.withMaps = withMaps;
module.exports.MAP_DEFAULTS = { tileUrl: DEFAULT_TILE_URL, attribution: DEFAULT_TILE_ATTRIBUTION, maxZoom: DEFAULT_MAX_ZOOM };
