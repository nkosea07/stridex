import * as SQLite from 'expo-sqlite';

export type TrackingDatabase = SQLite.SQLiteDatabase;

export const openDatabaseAsync = SQLite.openDatabaseAsync;
