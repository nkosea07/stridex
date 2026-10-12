// Auto-loads every app catalog under ./locales/en as an i18next namespace, so adding a
// namespace is just adding a JSON file (no code changes here).
const catalogs = import.meta.glob('./locales/en/*.json', { eager: true }) as Record<
  string,
  { default: Record<string, unknown> }
>

export const appNamespaces: Record<string, Record<string, unknown>> = {}
for (const [filePath, mod] of Object.entries(catalogs)) {
  const namespace = filePath.slice(filePath.lastIndexOf('/') + 1, -'.json'.length)
  appNamespaces[namespace] = mod.default
}

export const appNamespaceNames = Object.keys(appNamespaces)
