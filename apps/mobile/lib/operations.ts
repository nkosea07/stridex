import type { AssignedCheckpoint, CheckpointEmergencyReceipt } from './api';

export type OperationOption = { id: string; name: string };
export type OperationSession = { user: { id: string; role: string } | null; token: string | null; refreshToken: string | null };

export function createSingleFlight() {
  let running = false;
  return {
    get running() { return running; },
    async run<T>(action: () => Promise<T>): Promise<T | undefined> {
      if (running) return undefined;
      running = true;
      try {
        return await action();
      } finally {
        running = false;
      }
    },
  };
}

export function isCurrentOperation(
  requestEpoch: number,
  currentEpoch: number,
  focused: boolean,
  startedSession: OperationSession,
  currentSession: OperationSession,
): boolean {
  return focused && requestEpoch === currentEpoch
    && startedSession.user?.id === currentSession.user?.id
    && startedSession.user?.role === currentSession.user?.role
    && startedSession.token === currentSession.token
    && startedSession.refreshToken === currentSession.refreshToken;
}

export function assignedOptions(rows: AssignedCheckpoint[]): { races: OperationOption[]; checkpointsByRace: Record<string, OperationOption[]> } {
  const races: OperationOption[] = [];
  const checkpointsByRace: Record<string, OperationOption[]> = {};
  for (const row of rows) {
    if (!checkpointsByRace[row.raceId]) {
      races.push({ id: row.raceId, name: row.raceName });
      checkpointsByRace[row.raceId] = [];
    }
    if (!checkpointsByRace[row.raceId].some((checkpoint) => checkpoint.id === row.checkpointId)) {
      checkpointsByRace[row.raceId].push({ id: row.checkpointId, name: row.checkpointName });
    }
  }
  return { races, checkpointsByRace };
}

export function validSelection<T extends OperationOption>(current: string, options: T[]): string {
  return options.some((option) => option.id === current) ? current : '';
}

export function incidentMessage(draft: string): string | null {
  const message = draft.trim();
  return message && message.length <= 2000 ? message : null;
}

export function receiptCopyKey(receipt: CheckpointEmergencyReceipt): 'incidentDispatchedBody' | 'incidentReceivedBody' {
  return receipt.deliveryStatus === 'DISPATCHED' ? 'incidentDispatchedBody' : 'incidentReceivedBody';
}

export const STAFF_UNDO_WINDOW_MS = 5 * 60 * 1000;

const STAFF_ERROR_CODES = new Set([
  'CHECK_IN_NOT_ELIGIBLE',
  'QR_CHECK_IN_CODE_INVALID',
  'PARTICIPANT_SEARCH_INVALID',
  'CHECKPOINT_ALREADY_RECORDED',
  'PARTICIPANT_NOT_ENTERED',
  'RESULTS_CERTIFIED',
  'RECORD_UNDO_WINDOW_EXPIRED',
  'GUN_TIME_OFFICIAL_NOW_ONLY',
  'OFFLINE_CAPTURE_BEFORE_GUN',
  'OFFLINE_CAPTURE_OUT_OF_RANGE',
]);

export function staffErrorKey(error: unknown): string {
  const candidate = error as { code?: unknown; status?: unknown; isNetworkError?: unknown } | null;
  if (candidate?.isNetworkError) return 'mobile:staff.errors.network';
  if (typeof candidate?.code === 'string' && STAFF_ERROR_CODES.has(candidate.code)) {
    return `mobile:staff.errors.${candidate.code}`;
  }
  if (candidate?.status === 404) return 'mobile:staff.errors.notFound';
  if (candidate?.status === 403) return 'mobile:staff.errors.notAllowed';
  return 'mobile:staff.errors.generic';
}

export function canUndoEntry(
  record: { recordedById?: string | null; recordedAt?: string | null; recordMethod?: string | null },
  userId: string | null | undefined,
  enteredAt: number | null,
  now: number = Date.now(),
): boolean {
  if (!userId || record.recordedById !== userId) return false;
  if (record.recordMethod === 'RFID') return false;
  if (enteredAt === null) return false;
  return now - enteredAt < STAFF_UNDO_WINDOW_MS;
}
