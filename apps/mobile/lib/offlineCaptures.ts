import AsyncStorage from '@react-native-async-storage/async-storage';
import { ApiRequestError } from './api';

const STORAGE_KEY = 'operations.offlineCaptures.v1';
const MAX_QUEUED = 2000;

export type OfflineCapture = {
  id: string;
  raceId: string;
  checkpointId: string;
  bib: string;
  capturedAt: string;
};

export type FlushResult = {
  sent: number;
  rejected: { capture: OfflineCapture; message: string }[];
  remaining: number;
};

export async function loadOfflineCaptures(): Promise<OfflineCapture[]> {
  try {
    const raw = await AsyncStorage.getItem(STORAGE_KEY);
    const parsed = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? parsed.filter((item) => item && typeof item.bib === 'string') : [];
  } catch {
    return [];
  }
}

async function save(captures: OfflineCapture[]) {
  await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(captures));
}

export async function enqueueOfflineCapture(capture: Omit<OfflineCapture, 'id'>): Promise<number> {
  const queue = await loadOfflineCaptures();
  if (queue.length >= MAX_QUEUED) {
    throw new Error('Offline capture queue is full');
  }
  queue.push({ ...capture, id: `${capture.capturedAt}:${capture.checkpointId}:${capture.bib}` });
  await save(queue);
  return queue.length;
}

/**
 * Sends queued captures in the order they were taken. A network failure stops the flush and keeps
 * everything not yet sent; a capture the server already has is treated as sent; any other refusal
 * removes the capture and reports it so staff can re-enter it by hand.
 */
export async function flushOfflineCaptures(
  send: (capture: OfflineCapture) => Promise<void>,
): Promise<FlushResult> {
  const queue = await loadOfflineCaptures();
  const rejected: FlushResult['rejected'] = [];
  let sent = 0;
  let index = 0;
  for (; index < queue.length; index += 1) {
    const capture = queue[index];
    try {
      await send(capture);
      sent += 1;
    } catch (error) {
      if (error instanceof ApiRequestError && !error.isNetworkError && error.status !== undefined) {
        if (error.code === 'CHECKPOINT_ALREADY_RECORDED' || error.status === 409 && error.code === undefined) {
          sent += 1;
        } else if (error.status >= 500 || error.status === 401 || error.status === 429) {
          break;
        } else {
          rejected.push({ capture, message: error.message });
        }
        continue;
      }
      break;
    }
  }
  const remaining = queue.slice(index);
  await save(remaining);
  return { sent, rejected, remaining: remaining.length };
}

export async function clearOfflineCaptures(): Promise<void> {
  await AsyncStorage.removeItem(STORAGE_KEY);
}
