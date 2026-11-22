import AsyncStorage from '@react-native-async-storage/async-storage';

const INSTALL_ID_KEY = 'race_tracker_install_id';
const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

let pending: Promise<string> | null = null;

export function createInstallId(random: () => number = Math.random): string {
  const hex = Array.from({ length: 32 }, () => Math.floor(random() * 16).toString(16));
  hex[12] = '4';
  hex[16] = ((Number.parseInt(hex[16], 16) & 0x3) | 0x8).toString(16);
  const value = hex.join('');
  return `${value.slice(0, 8)}-${value.slice(8, 12)}-${value.slice(12, 16)}-${value.slice(16, 20)}-${value.slice(20)}`;
}

export function isInstallId(value: unknown): value is string {
  return typeof value === 'string' && UUID_V4.test(value);
}

async function loadOrCreate(): Promise<string> {
  try {
    const stored = await AsyncStorage.getItem(INSTALL_ID_KEY);
    if (isInstallId(stored)) return stored;
  } catch {
  }
  const created = createInstallId();
  try {
    await AsyncStorage.setItem(INSTALL_ID_KEY, created);
  } catch {
  }
  return created;
}

export function getInstallId(): Promise<string> {
  pending ??= loadOrCreate();
  return pending;
}

export function resetInstallIdCacheForTests(): void {
  pending = null;
}
