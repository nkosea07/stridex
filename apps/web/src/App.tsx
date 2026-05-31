import { createBrowserRouter, RouterProvider, type RouteObject } from 'react-router-dom'
import Root from '../app/root'
import HomeRoute from '../app/routes/home'
import LoginRoute from '../app/routes/login'
import RegisterRoute from '../app/routes/register'
import UnauthorizedRoute from '../app/routes/unauthorized'
import PublicRaceRoute from '../app/routes/public-race'
import PublicAthleteRoute from '../app/routes/public-athlete'
import AdminLayoutRoute from '../app/routes/admin-layout'
import AdminDashboardRoute from '../app/routes/admin-dashboard'
import AdminRacesRoute from '../app/routes/admin-races'
import AdminRaceDetailRoute from '../app/routes/admin-race-detail'
import AdminRaceParticipantsRoute from '../app/routes/admin-race-participants'
import AdminRaceLiveRoute from '../app/routes/admin-race-live'
import AdminRaceCheckpointsRoute from '../app/routes/admin-race-checkpoints'
import AdminRaceResultsRoute from '../app/routes/admin-race-results'
import AdminSettingsRoute from '../app/routes/admin-settings'
import AppLayoutRoute from '../app/routes/app-layout'
import AppDashboardRoute from '../app/routes/app-dashboard'
import AppStatsRoute from '../app/routes/app-stats'
import FallbackRoute from '../app/routes/fallback'

export const appRoutes: RouteObject[] = [
  {
    id: 'root',
    Component: Root,
    children: [
      { id: 'home', index: true, Component: HomeRoute },
      { id: 'login', path: 'login', Component: LoginRoute },
      { id: 'register', path: 'register', Component: RegisterRoute },
      { id: 'unauthorized', path: 'unauthorized', Component: UnauthorizedRoute },
      { id: 'public-race', path: 'race/:raceId', Component: PublicRaceRoute },
      {
        id: 'public-athlete',
        path: 'race/:raceId/athlete/:participantId',
        Component: PublicAthleteRoute,
      },
      {
        id: 'admin-layout',
        path: 'admin',
        Component: AdminLayoutRoute,
        children: [
          { id: 'admin-dashboard', index: true, Component: AdminDashboardRoute },
          { id: 'admin-races', path: 'races', Component: AdminRacesRoute },
          { id: 'admin-race-detail', path: 'races/:raceId', Component: AdminRaceDetailRoute },
          {
            id: 'admin-race-participants',
            path: 'races/:raceId/participants',
            Component: AdminRaceParticipantsRoute,
          },
          { id: 'admin-race-live', path: 'races/:raceId/live', Component: AdminRaceLiveRoute },
          {
            id: 'admin-race-checkpoints',
            path: 'races/:raceId/checkpoints',
            Component: AdminRaceCheckpointsRoute,
          },
          {
            id: 'admin-race-results',
            path: 'races/:raceId/results',
            Component: AdminRaceResultsRoute,
          },
          { id: 'admin-settings', path: 'settings', Component: AdminSettingsRoute },
        ],
      },
      {
        id: 'app-layout',
        path: 'app',
        Component: AppLayoutRoute,
        children: [
          { id: 'app-dashboard', index: true, Component: AppDashboardRoute },
          { id: 'app-stats', path: 'stats', Component: AppStatsRoute },
        ],
      },
      { id: 'fallback', path: '*', Component: FallbackRoute },
    ],
  },
]

const router = createBrowserRouter(appRoutes)

export default function App() {
  return <RouterProvider router={router} />
}
