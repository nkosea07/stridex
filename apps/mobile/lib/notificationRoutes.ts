export type NotificationRoute =
  | { pathname: '/athlete/[raceId]/[participantId]'; params: { raceId: string; participantId: string } }
  | { pathname: '/race/[id]'; params: { id: string } }
  | { pathname: '/(tabs)/results' }
  | { pathname: '/notifications' };

const ID_PATTERN = /^[0-9a-fA-F-]{8,64}$/;

function id(value: unknown): string | null {
  return typeof value === 'string' && ID_PATTERN.test(value) ? value : null;
}

export function routeForNotification(data: Record<string, unknown> | null | undefined): NotificationRoute {
  const kind = typeof data?.kind === 'string' ? data.kind : null;
  const raceId = id(data?.raceId);
  const participantId = id(data?.participantId);
  if (kind === 'FOLLOWED_ATHLETE' && raceId && participantId) {
    return { pathname: '/athlete/[raceId]/[participantId]', params: { raceId, participantId } };
  }
  if (kind === 'RESULTS' || kind === 'RESULT_QUERY') {
    return { pathname: '/(tabs)/results' };
  }
  if (raceId) {
    return { pathname: '/race/[id]', params: { id: raceId } };
  }
  return { pathname: '/notifications' };
}
