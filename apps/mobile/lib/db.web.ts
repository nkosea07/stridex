/**
 * Web-compatible mock for expo-sqlite.
 * Performs no-op operations to allow bundling on web without WASM dependencies.
 */

export const openDatabaseAsync = async (name: string) => {
  console.log(`[SQLite Mock] Database "${name}" opened (no-op on web)`);
  
  return {
    execAsync: async (sql: string) => {
      console.log(`[SQLite Mock] Executing SQL: ${sql.substring(0, 50)}...`);
      return;
    },
    runAsync: async (sql: string, params: any[] = []) => {
      console.log(`[SQLite Mock] Running SQL: ${sql.substring(0, 50)}... with ${params.length} params`);
      return { lastInsertRowId: 0, changes: 0 };
    },
    getAllAsync: async <T>(sql: string, params: any[] = []) => {
      console.log(`[SQLite Mock] Getting all: ${sql.substring(0, 50)}...`);
      return [] as T[];
    },
    getFirstAsync: async <T>(sql: string, params: any[] = []) => {
      return null as T | null;
    }
  };
};
