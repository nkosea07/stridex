import { lazy, Suspense } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider, isAdminRole, useAuth } from '@/contexts/AuthContext'
import { Toaster } from '@/components/ui/sonner'
import { DashboardLayout } from '@/components/layout/DashboardLayout'
import { RequireAdminRoute } from '@/components/auth/RequireAdminRoute'
import { RequireAuthenticatedRoute } from '@/components/auth/RequireAuthenticatedRoute'
import { UserLayout } from '@/components/layout/UserLayout'

// Lazy-loaded components
const LoginPage = lazy(() => import('@/pages/LoginPage').then((m) => ({ default: m.LoginPage })))
const RegisterPage = lazy(() => import('@/pages/RegisterPage').then((m) => ({ default: m.RegisterPage })))
const DashboardPage = lazy(() => import('@/pages/DashboardPage').then((m) => ({ default: m.DashboardPage })))
const RacesPage = lazy(() => import('@/pages/RacesPage').then((m) => ({ default: m.RacesPage })))
const RaceDetailPage = lazy(() => import('@/pages/RaceDetailsPage').then((m) => ({ default: m.RaceDetailPage })))
const ParticipantsPage = lazy(() => import('@/pages/ParticipantsPage').then((m) => ({ default: m.ParticipantsPage })))
const LiveTrackingPage = lazy(() => import('@/pages/LiveTrackingPage').then((m) => ({ default: m.LiveTrackingPage })))
const CheckpointsPage = lazy(() => import('@/pages/CheckpointsPage').then((m) => ({ default: m.CheckpointsPage })))
const ResultsPage = lazy(() => import('@/pages/ResultsPage').then((m) => ({ default: m.ResultsPage })))
const SettingsPage = lazy(() => import('@/pages/SettingsPage').then((m) => ({ default: m.SettingsPage })))
const UnauthorizedPage = lazy(() =>
  import('@/pages/UnauthorizedPage').then((m) => ({ default: m.UnauthorizedPage })),
)
const UserDashboardPage = lazy(() =>
  import('@/pages/UserDashboardPage').then((m) => ({ default: m.UserDashboardPage })),
)
const UserStatsPage = lazy(() => import('@/pages/UserStatsPage').then((m) => ({ default: m.UserStatsPage })))
const PublicRaceHubPage = lazy(() =>
  import('@/pages/PublicRaceHubPage').then((m) => ({ default: m.PublicRaceHubPage })),
)
const PublicAthleteTrackingPage = lazy(() =>
  import('@/pages/PublicAthleteTrackingPage').then((m) => ({ default: m.PublicAthleteTrackingPage })),
)

function HomeRedirect() {
  const { isLoading, isAuthenticated, user } = useAuth()

  if (isLoading) {
    return <div className="flex min-h-screen items-center justify-center text-sm text-muted-foreground">Loading...</div>
  }

  if (!isAuthenticated || !user) {
    return <Navigate to="/login" replace />
  }

  return <Navigate to={isAdminRole(user.role) ? '/admin' : '/app'} replace />
}

export default function App() {
  return (
    <AuthProvider>
      <Suspense fallback={<div>Loading...</div>}>
        <Routes>
          <Route path="/" element={<HomeRedirect />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/unauthorized" element={<UnauthorizedPage />} />
          <Route path="/race/:raceId" element={<PublicRaceHubPage />} />
          <Route path="/race/:raceId/athlete/:participantId" element={<PublicAthleteTrackingPage />} />
          <Route
            path="/admin"
            element={
              <RequireAdminRoute>
                <DashboardLayout />
              </RequireAdminRoute>
            }
          >
            <Route index element={<DashboardPage />} />
            <Route path="races" element={<RacesPage />} />
            <Route path="races/:raceId" element={<RaceDetailPage />} />
            <Route path="races/:raceId/participants" element={<ParticipantsPage />} />
            <Route path="races/:raceId/live" element={<LiveTrackingPage />} />
            <Route path="races/:raceId/checkpoints" element={<CheckpointsPage />} />
            <Route path="races/:raceId/results" element={<ResultsPage />} />
            <Route path="settings" element={<SettingsPage />} />
          </Route>
          <Route
            path="/app"
            element={
              <RequireAuthenticatedRoute>
                <UserLayout />
              </RequireAuthenticatedRoute>
            }
          >
            <Route index element={<UserDashboardPage />} />
            <Route path="stats" element={<UserStatsPage />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Suspense>
      <Toaster />
    </AuthProvider>
  )
}
