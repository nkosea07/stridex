import { ApiRequestError } from './api';

export type { SelfScanOutcome } from './api';

export type SelfScanCode = { checkpointId: string; raceId: string; token: string };
export type SelfScanParseResult =
  | { kind: 'ok'; code: SelfScanCode }
  | { kind: 'outdated' }
  | { kind: 'unreadable' };

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const TOKEN_PATTERN = /^[0-9a-f]{32,128}$/i;

const ERROR_CODES = new Set([
  'SELF_SCAN_CODE_INVALID',
  'SELF_SCAN_NOT_ALLOWED',
  'RACE_NOT_IN_PROGRESS',
  'CHECKPOINT_ALREADY_RECORDED',
  'TRACKING_CLOSED',
]);

export function parseSelfScanCode(data: unknown): SelfScanParseResult {
  if (typeof data !== 'string') return { kind: 'unreadable' };
  const parts = data.trim().split(':');
  if (parts.length === 2 && UUID_PATTERN.test(parts[0]) && UUID_PATTERN.test(parts[1])) {
    return { kind: 'outdated' };
  }
  if (parts.length !== 3) return { kind: 'unreadable' };
  const [checkpointId, raceId, token] = parts;
  if (!UUID_PATTERN.test(checkpointId) || !UUID_PATTERN.test(raceId) || !TOKEN_PATTERN.test(token)) {
    return { kind: 'unreadable' };
  }
  return { kind: 'ok', code: { checkpointId, raceId, token } };
}

export function selfScanErrorKey(error: unknown): string {
  const code = error instanceof ApiRequestError ? error.code : undefined;
  return code && ERROR_CODES.has(code) ? `mobile:scanner.errors.${code}` : 'mobile:scanner.failedBody';
}
