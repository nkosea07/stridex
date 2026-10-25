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
