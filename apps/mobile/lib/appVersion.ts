import type { AppConfig } from './api';

const VERSION = /^v?(\d{1,6})(?:\.(\d{1,6}))?(?:\.(\d{1,6}))?(?:[-+].*)?$/;

export function parseVersion(raw: string | null | undefined): [number, number, number] | null {
  const match = raw ? VERSION.exec(raw.trim()) : null;
  if (!match) return null;
  return [Number(match[1]), Number(match[2] ?? 0), Number(match[3] ?? 0)];
}

export function isVersionBelow(version: string | null | undefined, minimum: string | null | undefined): boolean {
  const current = parseVersion(version);
  const floor = parseVersion(minimum);
  if (!current || !floor) return false;
  for (let index = 0; index < 3; index += 1) {
    if (current[index] !== floor[index]) return current[index] < floor[index];
  }
  return false;
}

export function storeUrlFor(config: AppConfig | null, platform: string): string | null {
  if (!config) return null;
  if (platform === 'ios') return config.iosStoreUrl;
  if (platform === 'android') return config.androidStoreUrl;
  return null;
}
