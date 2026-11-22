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

module.exports = ({ config }) => withAppLinks(config, process.env.EXPO_PUBLIC_WEB_URL);
module.exports.withAppLinks = withAppLinks;
module.exports.APP_LINK_PATHS = APP_LINK_PATHS;
