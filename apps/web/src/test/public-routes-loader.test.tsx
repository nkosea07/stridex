import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { PublicAthleteTrackingPage } from '@/pages/PublicAthleteTrackingPage'
import { PublicRaceHubPage } from '@/pages/PublicRaceHubPage'
import * as publicAthleteRouteModule from '../../app/routes/public-athlete'
import * as publicRaceRouteModule from '../../app/routes/public-race'
import { mediaApi, publicTrackingApi } from '@/lib/api'
import { PUBLIC_BROWSER_DEMO_RACE_ID } from '@/router/publicLoaders'
import '@/i18n'
import { SWRConfig } from 'swr'

vi.mock('@/lib/api', () => ({
  publicTrackingApi: {
    getRaceHub: vi.fn(),
    getLiveAthletes: vi.fn(),
    getLiveFeed: vi.fn(),
    searchAthletes: vi.fn(),
    getAthleteTracking: vi.fn(),
    getAthleteTrack: vi.fn().mockResolvedValue({ points: [] }),
    getHeatmap: vi.fn().mockResolvedValue([]),
  },
  mediaApi: {
    publicList: vi.fn().mockResolvedValue([]),
  },
  favoritesApi: {
    list: vi.fn().mockResolvedValue([]),
    follow: vi.fn(),
    unfollow: vi.fn(),
  },
}))

const getRaceHubMock = vi.mocked(publicTrackingApi.getRaceHub)
const getLiveAthletesMock = vi.mocked(publicTrackingApi.getLiveAthletes)
const getLiveFeedMock = vi.mocked(publicTrackingApi.getLiveFeed)
const searchAthletesMock = vi.mocked(publicTrackingApi.searchAthletes)
const getAthleteTrackingMock = vi.mocked(publicTrackingApi.getAthleteTracking)
const getHeatmapMock = vi.mocked(publicTrackingApi.getHeatmap)
const publicMediaListMock = vi.mocked(mediaApi.publicList)
const API_RACE_ID = '123e4567-e89b-12d3-a456-426614174000'

type PublicRaceLoaderArgs = Parameters<typeof publicRaceRouteModule.clientLoader>[0]
type PublicAthleteLoaderArgs = Parameters<typeof publicAthleteRouteModule.clientLoader>[0]

function createRaceLoaderArgs(params: PublicRaceLoaderArgs['params']): PublicRaceLoaderArgs {
  return {
    params,
    request: new Request(`http://localhost/race/${params.raceId}`),
    context: undefined as never,
    serverLoader: async () => undefined,
    url: new URL(`http://localhost/race/${params.raceId}`),
    pattern: '/race/:raceId',
  } as PublicRaceLoaderArgs
}

function createAthleteLoaderArgs(params: PublicAthleteLoaderArgs['params']): PublicAthleteLoaderArgs {
  return {
    params,
    request: new Request(`http://localhost/race/${params.raceId}/athlete/${params.participantId}`),
    context: undefined as never,
    serverLoader: async () => undefined,
    url: new URL(`http://localhost/race/${params.raceId}/athlete/${params.participantId}`),
    pattern: '/race/:raceId/athlete/:participantId',
  } as PublicAthleteLoaderArgs
}

describe('public route loaders', () => {
  afterEach(() => {
    cleanup()
    vi.clearAllMocks()
    vi.useRealTimers()
  })

  it('throws a 404 response when the race hub loader is missing race params', async () => {
    await expect(
      publicRaceRouteModule.clientLoader({
        params: {},
        request: new Request('http://localhost/race'),
        context: undefined as never,
        serverLoader: async () => undefined,
        url: new URL('http://localhost/race'),
        pattern: '/race/:raceId',
      } as unknown as PublicRaceLoaderArgs),
    ).rejects.toMatchObject({
      status: 404,
      statusText: '',
    })
  })

  it('throws a 404 response when the athlete loader is missing route params', async () => {
    await expect(
      publicAthleteRouteModule.clientLoader({
        params: { raceId: API_RACE_ID },
        request: new Request(`http://localhost/race/${API_RACE_ID}/athlete`),
        context: undefined as never,
        serverLoader: async () => undefined,
        url: new URL(`http://localhost/race/${API_RACE_ID}/athlete`),
        pattern: '/race/:raceId/athlete/:participantId',
      } as unknown as PublicAthleteLoaderArgs),
    ).rejects.toMatchObject({
      status: 404,
      statusText: '',
    })
  })

  it('loads the browser demo from static data without invoking the public API', async () => {
    const initialData = await publicRaceRouteModule.clientLoader(
      createRaceLoaderArgs({ raceId: PUBLIC_BROWSER_DEMO_RACE_ID }),
    )

    render(
      <MemoryRouter initialEntries={[`/race/${PUBLIC_BROWSER_DEMO_RACE_ID}`]}>
        <Routes>
          <Route
            path="/race/:raceId"
            element={
              <PublicRaceHubPage
                raceId={PUBLIC_BROWSER_DEMO_RACE_ID}
                initialData={initialData}
              />
            }
          />
        </Routes>
      </MemoryRouter>,
    )

    expect(initialData).toEqual({
      raceId: PUBLIC_BROWSER_DEMO_RACE_ID,
      hub: {
        raceId: PUBLIC_BROWSER_DEMO_RACE_ID,
        raceName: 'Race Tracker Browser Demo',
        status: 'IN_PROGRESS',
        raceDate: '2026-05-31T06:00:00.000Z',
        city: 'Harare',
        country: 'Zimbabwe',
        distanceKm: 42.2,
        leaderboard: [],
      },
      liveAthletes: [],
    })
    expect(await screen.findByText(/Race Tracker Browser Demo/i)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /refresh/i })).not.toBeInTheDocument()

    vi.useFakeTimers()
    fireEvent.change(screen.getByPlaceholderText(/Bib 1024/i), { target: { value: '101' } })
    await act(async () => {
      await vi.advanceTimersByTimeAsync(180)
    })

    expect(getRaceHubMock).not.toHaveBeenCalled()
    expect(getLiveFeedMock).not.toHaveBeenCalled()
    expect(getHeatmapMock).not.toHaveBeenCalled()
    expect(publicMediaListMock).not.toHaveBeenCalled()
    expect(searchAthletesMock).not.toHaveBeenCalled()
  })

  it('renders the unavailable state without race or loader data', () => {
    const { container } = render(
      <MemoryRouter>
        <PublicRaceHubPage />
      </MemoryRouter>,
    )

    expect(container.firstChild).toHaveClass('min-h-screen', 'bg-slate-950')
  })

  it('resets UUID race state when navigating to the static browser demo', async () => {
    getRaceHubMock.mockResolvedValue({
      raceId: API_RACE_ID,
      raceName: 'Demo Race',
      status: 'IN_PROGRESS',
      raceDate: '2026-05-31T06:00:00.000Z',
      city: 'Harare',
      country: 'Zimbabwe',
      distanceKm: 42.2,
      leaderboard: [],
    })
    getLiveFeedMock.mockResolvedValue({
      items: [{
        participantId: 'athlete-1',
        participantName: 'Demo Athlete',
        bibNumber: '101',
        status: 'STARTED',
        lastCheckpoint: '10K',
        elapsedTime: '00:40:00',
      }],
      nextCursor: null,
      refreshedAt: '2026-05-31T06:00:00.000Z',
    })

    const apiData = await publicRaceRouteModule.clientLoader(createRaceLoaderArgs({ raceId: API_RACE_ID }))
    const demoData = await publicRaceRouteModule.clientLoader(
      createRaceLoaderArgs({ raceId: PUBLIC_BROWSER_DEMO_RACE_ID }),
    )
    const { rerender } = render(
      <MemoryRouter initialEntries={[`/race/${API_RACE_ID}`]}>
        <PublicRaceHubPage raceId={API_RACE_ID} initialData={apiData} />
      </MemoryRouter>,
    )

    expect(await screen.findByText(/Demo Race/i)).toBeInTheDocument()
    expect(await screen.findByText(/Demo Athlete/i)).toBeInTheDocument()
    fireEvent.change(screen.getByPlaceholderText(/Bib 1024/i), { target: { value: '101' } })
    expect(screen.getByDisplayValue('101')).toBeInTheDocument()

    vi.clearAllMocks()
    rerender(
      <MemoryRouter initialEntries={[`/race/${PUBLIC_BROWSER_DEMO_RACE_ID}`]}>
        <PublicRaceHubPage raceId={PUBLIC_BROWSER_DEMO_RACE_ID} initialData={demoData} />
      </MemoryRouter>,
    )
    window.dispatchEvent(new Event('focus'))
    window.dispatchEvent(new Event('online'))

    expect(await screen.findByText(/Race Tracker Browser Demo/i)).toBeInTheDocument()
    expect(await screen.findByText(/No athletes matched that search/i)).toBeInTheDocument()
    expect(screen.getByPlaceholderText(/Bib 1024/i)).toHaveValue('')
    expect(screen.queryByText(/Demo Race/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/Demo Athlete/i)).not.toBeInTheDocument()
    expect(getRaceHubMock).not.toHaveBeenCalled()
    expect(getLiveFeedMock).not.toHaveBeenCalled()
    expect(getHeatmapMock).not.toHaveBeenCalled()
    expect(publicMediaListMock).not.toHaveBeenCalled()
    expect(searchAthletesMock).not.toHaveBeenCalled()
  })

  it('loads race hub data through the route loader without duplicate initial transport calls', async () => {
    expect(typeof publicRaceRouteModule.clientLoader).toBe('function')

    getRaceHubMock.mockResolvedValue({
      raceId: API_RACE_ID,
      raceName: 'Demo Race',
      status: 'IN_PROGRESS',
      raceDate: '2026-05-31T06:00:00.000Z',
      city: 'Harare',
      country: 'Zimbabwe',
      distanceKm: 42.2,
      leaderboard: [],
    })
    getLiveFeedMock.mockResolvedValue({
      items: [{
        participantId: 'athlete-1',
        participantName: 'Demo Athlete',
        bibNumber: '101',
        status: 'STARTED',
        lastCheckpoint: '10K',
        elapsedTime: '00:40:00',
      }],
      nextCursor: null,
      refreshedAt: '2026-05-31T06:00:00.000Z',
    })

    const initialData = await publicRaceRouteModule.clientLoader(createRaceLoaderArgs({ raceId: API_RACE_ID }))

    render(
      <MemoryRouter initialEntries={[`/race/${API_RACE_ID}`]}>
        <Routes>
          <Route path="/race/:raceId" element={<PublicRaceHubPage raceId={API_RACE_ID} initialData={initialData} />} />
        </Routes>
      </MemoryRouter>,
    )

    expect(await screen.findByText(/Demo Race/i)).toBeInTheDocument()
    expect(await screen.findByText(/Search Results/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /refresh/i })).toBeInTheDocument()

    await waitFor(() => {
      expect(getRaceHubMock).toHaveBeenCalledTimes(1)
      expect(getLiveFeedMock).toHaveBeenCalledTimes(1)
    })

    expect(getRaceHubMock).toHaveBeenCalledWith(API_RACE_ID)
    expect(getLiveFeedMock).toHaveBeenCalledWith(API_RACE_ID, { limit: 100 })
    expect(getLiveAthletesMock).not.toHaveBeenCalled()
    expect(searchAthletesMock).not.toHaveBeenCalled()
  })

  it('removes a previously visible race when refresh is denied', async () => {
    const raceId = '123e4567-e89b-12d3-a456-426614174011'
    getRaceHubMock.mockRejectedValue(new Error('404'))
    getLiveFeedMock.mockResolvedValue({ items: [], nextCursor: null, refreshedAt: '' })
    render(
      <SWRConfig value={{ provider: () => new Map(), dedupingInterval: 0 }}>
        <MemoryRouter initialEntries={[`/race/${raceId}`]}>
          <PublicRaceHubPage raceId={raceId} initialData={{ raceId, hub: {
            raceId, raceName: 'Formerly Visible Race', status: 'IN_PROGRESS',
            raceDate: '2026-05-31T06:00:00.000Z', city: 'Harare', country: 'Zimbabwe',
            distanceKm: 42.2, leaderboard: [],
          }, liveAthletes: [] }} />
        </MemoryRouter>
      </SWRConfig>,
    )

    expect(screen.getByText(/Formerly Visible Race/i)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /refresh/i }))
    expect(await screen.findByText(/Public race data is not available/i)).toBeInTheDocument()
    expect(screen.queryByText(/Formerly Visible Race/i)).not.toBeInTheDocument()
  })

  it('removes previously visible athlete progress when refresh is denied', async () => {
    const raceId = '123e4567-e89b-12d3-a456-426614174012'
    const participantId = 'athlete-hidden'
    getAthleteTrackingMock.mockRejectedValue(new Error('404'))
    render(
      <SWRConfig value={{ provider: () => new Map(), dedupingInterval: 0 }}>
        <MemoryRouter initialEntries={[`/race/${raceId}/athlete/${participantId}`]}>
          <PublicAthleteTrackingPage raceId={raceId} participantId={participantId}
            initialData={{ raceId, participantId, tracking: {
              participantId, participantName: 'Formerly Visible Athlete', bibNumber: '101',
              status: 'STARTED', lastCheckpoint: '10K', elapsedTime: '00:40:00',
              averagePace: '04:00', predictedFinishTime: '2026-05-31T08:48:00.000Z',
              distanceRemaining: 12.2, splits: [],
            } }} />
        </MemoryRouter>
      </SWRConfig>,
    )

    expect(screen.getByText(/Formerly Visible Athlete/i)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /refresh/i }))
    expect(await screen.findByText(/Athlete tracking is not available/i)).toBeInTheDocument()
    expect(screen.queryByText(/Formerly Visible Athlete/i)).not.toBeInTheDocument()
  })

  it('loads athlete tracking through the route loader without duplicate initial transport calls', async () => {
    expect(typeof publicAthleteRouteModule.clientLoader).toBe('function')

    getAthleteTrackingMock.mockResolvedValue({
      participantId: 'athlete-1',
      participantName: 'Demo Athlete',
      bibNumber: '101',
      status: 'STARTED',
      lastCheckpoint: '10K',
      elapsedTime: '00:40:00',
      averagePace: '04:00',
      predictedFinishTime: '2026-05-31T08:48:00.000Z',
      distanceRemaining: 12.2,
      splits: [],
    })

    const initialData = await publicAthleteRouteModule.clientLoader(
      createAthleteLoaderArgs({ raceId: API_RACE_ID, participantId: 'athlete-1' }),
    )

    render(
      <MemoryRouter initialEntries={[`/race/${API_RACE_ID}/athlete/athlete-1`]}>
        <Routes>
          <Route
            path="/race/:raceId/athlete/:participantId"
            element={
              <PublicAthleteTrackingPage
                raceId={API_RACE_ID}
                participantId="athlete-1"
                initialData={initialData}
              />
            }
          />
        </Routes>
      </MemoryRouter>,
    )

    expect((await screen.findAllByText(/Demo Athlete/i)).length).toBeGreaterThan(0)
    expect(await screen.findByText(/Public athlete tracking with checkpoint progress and projected finish timing\./i)).toBeInTheDocument()

    await waitFor(() => {
      expect(getAthleteTrackingMock).toHaveBeenCalledTimes(1)
    })

    expect(getAthleteTrackingMock).toHaveBeenCalledWith(API_RACE_ID, 'athlete-1')
  })
})
