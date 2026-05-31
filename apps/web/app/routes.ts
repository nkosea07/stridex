import { index, route, type RouteConfig } from '@react-router/dev/routes'

export default [
  index('routes/home.tsx'),
  route('login', 'routes/login.tsx'),
  route('register', 'routes/register.tsx'),
  route('unauthorized', 'routes/unauthorized.tsx'),
  route('race/:raceId', 'routes/public-race.tsx'),
  route('race/:raceId/athlete/:participantId', 'routes/public-athlete.tsx'),
  route('admin', 'routes/admin-layout.tsx', [
    index('routes/admin-dashboard.tsx'),
    route('races', 'routes/admin-races.tsx'),
    route('races/:raceId', 'routes/admin-race-detail.tsx'),
    route('races/:raceId/participants', 'routes/admin-race-participants.tsx'),
    route('races/:raceId/live', 'routes/admin-race-live.tsx'),
    route('races/:raceId/checkpoints', 'routes/admin-race-checkpoints.tsx'),
    route('races/:raceId/results', 'routes/admin-race-results.tsx'),
    route('settings', 'routes/admin-settings.tsx'),
  ]),
  route('app', 'routes/app-layout.tsx', [
    index('routes/app-dashboard.tsx'),
    route('stats', 'routes/app-stats.tsx'),
  ]),
  route('*', 'routes/fallback.tsx'),
] satisfies RouteConfig
