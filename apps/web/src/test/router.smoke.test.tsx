import { cleanup, render, screen, waitFor } from '@testing-library/react'
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

let mockSessionRole: 'admin' | 'athlete' = 'athlete'

function startBrowserSession(role: 'admin' | 'athlete') {
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
    const role = mockSessionRole === 'admin' ? 'SUPER_ADMIN' : 'ATHLETE'

    return new Response(
      JSON.stringify({
        id: `${role.toLowerCase()}-user`,
        email: role === 'SUPER_ADMIN' ? 'admin@example.com' : 'athlete@example.com',
        firstName: role === 'SUPER_ADMIN' ? 'Admin' : 'Athlete',
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
        raceId: 'demo',
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

  if (url.endsWith('/races')) {
    return new Response(JSON.stringify({ content: [] }), {
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
    ['/race/demo', '/race/demo', () => screen.findByText(/Public Race Hub/i), () => undefined],
    [
      '/race/demo/athlete/demo-athlete',
      '/race/demo/athlete/demo-athlete',
      () => screen.findByText(/Public athlete tracking with checkpoint progress and projected finish timing\./i),
      () => undefined,
    ],
    ['/login', '/login', () => screen.findByRole('heading', { name: /sign in/i }), () => undefined],
    ['/admin', '/admin', () => screen.findByRole('heading', { name: /^dashboard$/i }), () => startBrowserSession('admin')],
    ['/app', '/app', () => screen.findByRole('heading', { name: /my dashboard/i }), () => startBrowserSession('athlete')],
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
})
