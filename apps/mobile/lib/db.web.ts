/**
 * Web-compatible mock for expo-sqlite.
 * Performs no-op operations to allow bundling on web without WASM dependencies.
 */

export const openDatabaseAsync = async (_name: string) => ({
  execAsync: async (_sql: string) => undefined,
  runAsync: async (_sql: string, _params: any[] = []) => ({ lastInsertRowId: 0, changes: 0 }),
  getAllAsync: async <T>(_sql: string, _params: any[] = []) => [] as T[],
  getFirstAsync: async <T>(_sql: string, _params: any[] = []) => null as T | null,
});
