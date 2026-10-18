import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import routes from '../../app/routes'
import Root from '../../app/root'
import type { RouteConfigEntry } from '@react-router/dev/routes'
import type { RouteObject } from 'react-router-dom'
import { setAccessToken } from '@/lib/api'

const routeModules = import.meta.glob('../../app/**/*.tsx', { eager: true }) as Record<
  string,
  { default?: RouteObject['Component'] }
>

function getRouteModule(file: string): NonNullable<RouteObject['Component']> {
  const routeModule = routeModules[`../../app/${file}`]

  if (!routeModule?.default) {
    throw new Error(`Missing route module for ${file}`)
  }

  return routeModule.default
}

function toRouteObjects(config: RouteConfigEntry[]): RouteObject[] {
  return config.map((entry) => {
    const Component = getRouteModule(entry.file)
    const children = entry.children ? toRouteObjects(entry.children) : undefined

    if (entry.index) {
      return {
        index: true,
        Component,
      }
    }

    return {
      path: entry.path,
      Component,
      children,
    }
  })
}

const runtimeRoutes: RouteObject[] = [
  {
    Component: Root,
    children: toRouteObjects(routes),
  },
]

type MockSessionRole = 'admin' | 'raceAdmin' | 'official' | 'volunteer' | 'athlete' | 'spectator'
let mockSessionRole: MockSessionRole = 'athlete'
let failNextRaceList = false
let failNextDashboardStats = false
let raceListPayload: { content: unknown[] } = { content: [] }
const API_RACE_ID = '123e4567-e89b-12d3-a456-426614174000'

function startBrowserSession(role: MockSessionRole) {
  mockSessionRole = role
  document.cookie = `rt_browser_csrf=${role}-csrf; path=/`
}

const fetchMock = vi.fn(async (input: RequestInfo | URL, _init?: RequestInit) => {
  const url = String(input)

  if (url.includes('/auth/browser/refresh')) {
    return new Response(JSON.stringify({ token: `${mockSessionRole}-token` }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    })
  }

  if (url.includes('/auth/me')) {
    const role = mockSessionRole === 'admin'
      ? 'SUPER_ADMIN'
      : mockSessionRole === 'raceAdmin'
        ? 'RACE_ADMIN'
      : mockSessionRole === 'official'
        ? 'OFFICIAL'
        : mockSessionRole === 'volunteer'
          ? 'VOLUNTEER'
          : mockSessionRole === 'spectator'
            ? 'SPECTATOR'
            : 'ATHLETE'

    return new Response(
      JSON.stringify({
        id: `${role.toLowerCase()}-user`,
        email: role === 'SUPER_ADMIN' ? 'admin@example.com' : `${role.toLowerCase()}@example.com`,
        firstName: role === 'SUPER_ADMIN' ? 'Admin' : role === 'OFFICIAL' ? 'Official' : role === 'VOLUNTEER' ? 'Volunteer' : 'Athlete',
        lastName: 'Demo',
        role,
        isActive: true,
        emailVerified: true,
        createdAt: new Date().toISOString(),
      }),
      {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      },
    )
  }

  if (url.includes('/public/races/') && url.includes('/media')) {
    return new Response(JSON.stringify([]), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    })
  }

  if (url.includes('/public/races/') && url.includes('/athletes/live')) {
    return new Response(JSON.stringify([]), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    })
  }

  if (url.includes('/public/races/') && url.includes('/tracking/live-feed')) {
    return new Response(JSON.stringify({ items: [], nextCursor: null, refreshedAt: new Date().toISOString() }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    })
  }

  if (url.includes('/public/races/') && url.includes('/tracking/heatmap')) {
    return new Response(JSON.stringify([]), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    })
  }

  if (url.includes('/public/races/') && url.includes('/athletes/')) {
    return new Response(
      JSON.stringify({
        participantId: 'demo-athlete',
        participantName: 'Demo Athlete',
        bibNumber: '101',
        status: 'STARTED',
        lastCheckpoint: 'Checkpoint 1',
        elapsedTime: '00:32:10',
        averagePace: '04:35',
        predictedFinishTime: new Date().toISOString(),
        distanceRemaining: 12.4,
        splits: [],
      }),
      {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      },
    )
  }

  if (url.includes('/public/races/')) {
    return new Response(
      JSON.stringify({
        raceId: API_RACE_ID,
        raceName: 'Demo Race',
        status: 'IN_PROGRESS',
        raceDate: new Date().toISOString(),
        city: 'Testville',
        country: 'Zimbabwe',
        distanceKm: 42.2,
        leaderboard: [],
      }),
      {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      },
    )
  }

  if (url.includes('/dashboard/stats')) {
    if (failNextDashboardStats) {
      failNextDashboardStats = false
      return new Response(JSON.stringify({ message: 'temporary failure' }), {
        status: 503,
        headers: { 'Content-Type': 'application/json' },
      })
    }
    return new Response(
      JSON.stringify({
        totalRaces: 12,
        activeRaces: 2,
        totalParticipants: 480,
        liveTracking: 120,
        upcomingRaces: 3,
        completedToday: 1,
      }),
      {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      },
    )
  }

  if (/\/races(?:\?|$)/.test(url)) {
    if (failNextRaceList) {
      failNextRaceList = false
      return new Response(JSON.stringify({ message: 'temporary failure' }), {
        status: 503,
        headers: { 'Content-Type': 'application/json' },
      })
    }
    return new Response(JSON.stringify(raceListPayload), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    })
  }

  return new Response(JSON.stringify({}), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
})

describe('appRoutes', () => {
  beforeAll(() => {
    vi.stubGlobal('Request', Request)
    vi.stubGlobal('AbortController', AbortController)
    vi.stubGlobal('AbortSignal', AbortSignal)
    vi.stubGlobal(
      'matchMedia',
      vi.fn().mockImplementation(() => ({
        matches: false,
        media: '',
        onchange: null,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        addListener: vi.fn(),
        removeListener: vi.fn(),
        dispatchEvent: vi.fn(),
      })),
    )
    vi.stubGlobal('fetch', fetchMock)
  })

  beforeEach(() => {
    localStorage.clear()
    document.cookie = 'rt_browser_csrf=; Max-Age=0; path=/'
    setAccessToken(null)
    mockSessionRole = 'athlete'
    failNextRaceList = false
    failNextDashboardStats = false
    raceListPayload = { content: [] }
    fetchMock.mockClear()
  })

  afterEach(() => {
    cleanup()
  })

  afterAll(() => {
    vi.unstubAllGlobals()
  })

  it.each([
    ['/', '/', () => screen.findByRole('heading', { name: /race-day control/i }), () => undefined],
    [`/race/${API_RACE_ID}`, `/race/${API_RACE_ID}`, () => screen.findByText(/Public Race Hub/i), () => undefined],
    [
      `/race/${API_RACE_ID}/athlete/demo-athlete`,
      `/race/${API_RACE_ID}/athlete/demo-athlete`,
      () => screen.findByText(/Public athlete tracking with checkpoint progress and projected finish timing\./i),
      () => undefined,
    ],
    ['/login', '/login', () => screen.findByRole('heading', { name: /sign in/i }), () => undefined],
    ['/admin', '/admin', () => screen.findByRole('heading', { name: /^overview$/i }), () => startBrowserSession('admin')],
    ['/app', '/app', () => screen.findByRole('heading', { name: /my dashboard/i }), () => startBrowserSession('athlete')],
    ['/app', '/app', () => screen.findByRole('heading', { name: /spectator dashboard/i }), () => startBrowserSession('spectator')],
  ])('resolves %s', async (initialEntry, expectedPathname, findExpectedContent, setup) => {
    setup()

    const router = createMemoryRouter(runtimeRoutes, {
      initialEntries: [initialEntry],
    })

    render(<RouterProvider router={router} />)

    await waitFor(() => {
      expect(router.state.location.pathname).toBe(expectedPathname)
    })

    if (initialEntry === '/admin') {
      await waitFor(() => {
        expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/auth/browser/refresh'))).toBe(true)
        const meCalls = fetchMock.mock.calls.filter(([url]) => String(url).includes('/auth/me'))
        expect(meCalls.length).toBeGreaterThan(0)
        expect(meCalls.every(([, init]) => new Headers(init?.headers).get('Authorization') === 'Bearer admin-token')).toBe(true)
      })
    }

    expect(await findExpectedContent()).toBeInTheDocument()
  })

  it('renders translated admin navigation labels', async () => {
    startBrowserSession('admin')
    const router = createMemoryRouter(runtimeRoutes, { initialEntries: ['/admin'] })

    render(<RouterProvider router={router} />)

    expect(await screen.findByRole('link', { name: 'Overview' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Race management' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Organizer approvals' })).toBeInTheDocument()
    expect(await screen.findByText(/No races have been created yet/i)).toBeInTheDocument()
  })

  it.each(['official', 'volunteer'] as const)('routes %s to the stable race operations surface', async (role) => {
    startBrowserSession(role)
    const router = createMemoryRouter(runtimeRoutes, { initialEntries: ['/admin'] })

    render(<RouterProvider router={router} />)

    expect(await screen.findByRole('heading', { name: /race operations/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Race operations' })).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: /No races are available yet/i })).toBeInTheDocument()
    expect(screen.getByText(/Operations available to your role/i)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Create your first race/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Overview' })).not.toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/dashboard/stats'))).toBe(false)
  })

  it('shows a retryable race-list error without hiding the operations surface', async () => {
    failNextRaceList = true
    startBrowserSession('official')
    const router = createMemoryRouter(runtimeRoutes, { initialEntries: ['/admin'] })

    render(<RouterProvider router={router} />)

    expect(await screen.findByText(/Race list unavailable/i)).toBeInTheDocument()
    const retryButton = screen.getByRole('button', { name: /Retry/i })
    fireEvent.click(retryButton)
    expect(await screen.findByRole('heading', { name: /No races are available yet/i })).toBeInTheDocument()
  })

  it('does not leave stale race actions visible after a failed refresh', async () => {
    raceListPayload = {
      content: [{
        id: 'race-1',
        name: 'Visible Race',
        raceType: 'MARATHON',
        status: 'DRAFT',
        city: 'Harare',
        country: 'Zimbabwe',
        raceDate: new Date().toISOString(),
      }],
    }
    startBrowserSession('admin')
    const router = createMemoryRouter(runtimeRoutes, { initialEntries: ['/admin/races'] })

    render(<RouterProvider router={router} />)

    expect(await screen.findByText('Visible Race')).toBeInTheDocument()
    failNextRaceList = true
    fireEvent.click(screen.getByRole('button', { name: /Show Archived/i }))

    expect(await screen.findByText(/Race list unavailable/i)).toBeInTheDocument()
    expect(screen.queryByText('Visible Race')).not.toBeInTheDocument()
  })

  it('does not present failed dashboard statistics as valid zero values', async () => {
    failNextDashboardStats = true
    startBrowserSession('admin')
    const router = createMemoryRouter(runtimeRoutes, { initialEntries: ['/admin'] })

    render(<RouterProvider router={router} />)

    expect(await screen.findByText(/Dashboard data is incomplete/i)).toBeInTheDocument()
    expect(screen.getAllByText('—')).toHaveLength(4)
  })

  it('admits race administrators to the management shell', async () => {
    startBrowserSession('raceAdmin')
    const router = createMemoryRouter(runtimeRoutes, { initialEntries: ['/admin'] })

    render(<RouterProvider router={router} />)

    expect(await screen.findByRole('heading', { name: /^overview$/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Race management' })).toBeInTheDocument()
  })

  it('rejects spectators from the operations shell', async () => {
    startBrowserSession('spectator')
    const router = createMemoryRouter(runtimeRoutes, { initialEntries: ['/admin'] })

    render(<RouterProvider router={router} />)

    expect(await screen.findByRole('heading', { name: /access denied/i })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/unauthorized')
  })

  it('keeps race operations staff out of the personal app shell', async () => {
    startBrowserSession('official')
    const router = createMemoryRouter(runtimeRoutes, { initialEntries: ['/app'] })

    render(<RouterProvider router={router} />)

    expect(await screen.findByRole('heading', { name: /access denied/i })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/unauthorized')
  })

  it('does not advertise Google OAuth on the current password login surface', async () => {
    const router = createMemoryRouter(runtimeRoutes, {
      initialEntries: ['/login'],
    })

    render(<RouterProvider router={router} />)

    expect(await screen.findByRole('heading', { name: /sign in/i })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /google/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /google/i })).not.toBeInTheDocument()
  })
})
