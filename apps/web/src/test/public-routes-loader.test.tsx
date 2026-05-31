import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { PublicAthleteTrackingPage } from '@/pages/PublicAthleteTrackingPage'
import { PublicRaceHubPage } from '@/pages/PublicRaceHubPage'
import * as publicAthleteRouteModule from '../../app/routes/public-athlete'
import * as publicRaceRouteModule from '../../app/routes/public-race'
import { publicTrackingApi } from '@/lib/api'

vi.mock('@/lib/api', () => ({
  publicTrackingApi: {
    getRaceHub: vi.fn(),
    getLiveAthletes: vi.fn(),
    searchAthletes: vi.fn(),
    getAthleteTracking: vi.fn(),
  },
}))

const getRaceHubMock = vi.mocked(publicTrackingApi.getRaceHub)
const getLiveAthletesMock = vi.mocked(publicTrackingApi.getLiveAthletes)
const searchAthletesMock = vi.mocked(publicTrackingApi.searchAthletes)
const getAthleteTrackingMock = vi.mocked(publicTrackingApi.getAthleteTracking)

describe('public route loaders', () => {
  afterEach(() => {
    vi.clearAllMocks()
  })

  it('throws a 404 response when the race hub loader is missing race params', async () => {
    await expect(
      publicRaceRouteModule.clientLoader({
        params: {},
        request: new Request('http://localhost/race'),
        context: undefined as never,
        serverLoader: async () => undefined,
      }),
    ).rejects.toMatchObject({
      status: 404,
      statusText: '',
    })
  })

  it('throws a 404 response when the athlete loader is missing route params', async () => {
    await expect(
      publicAthleteRouteModule.clientLoader({
        params: { raceId: 'demo' },
        request: new Request('http://localhost/race/demo/athlete'),
        context: undefined as never,
        serverLoader: async () => undefined,
      }),
    ).rejects.toMatchObject({
      status: 404,
      statusText: '',
    })
  })

  it('loads race hub data through the route loader without duplicate initial transport calls', async () => {
    expect(typeof publicRaceRouteModule.clientLoader).toBe('function')

    getRaceHubMock.mockResolvedValue({
      raceId: 'demo',
      raceName: 'Demo Race',
      status: 'IN_PROGRESS',
      raceDate: '2026-05-31T06:00:00.000Z',
      city: 'Harare',
      country: 'Zimbabwe',
      distanceKm: 42.2,
      leaderboard: [],
    })
    getLiveAthletesMock.mockResolvedValue([
      {
        participantId: 'athlete-1',
        participantName: 'Demo Athlete',
        bibNumber: '101',
        status: 'STARTED',
        lastCheckpoint: '10K',
        elapsedTime: '00:40:00',
      },
    ])

    const initialData = await publicRaceRouteModule.clientLoader({
      params: { raceId: 'demo' },
      request: new Request('http://localhost/race/demo'),
      context: undefined as never,
      serverLoader: async () => undefined,
    })

    render(
      <MemoryRouter initialEntries={['/race/demo']}>
        <Routes>
          <Route path="/race/:raceId" element={<PublicRaceHubPage raceId="demo" initialData={initialData} />} />
        </Routes>
      </MemoryRouter>,
    )

    expect(await screen.findByText(/Demo Race/i)).toBeInTheDocument()
    expect(await screen.findByText(/Search Results/i)).toBeInTheDocument()

    await waitFor(() => {
      expect(getRaceHubMock).toHaveBeenCalledTimes(1)
      expect(getLiveAthletesMock).toHaveBeenCalledTimes(1)
    })

    expect(getRaceHubMock).toHaveBeenCalledWith('demo')
    expect(getLiveAthletesMock).toHaveBeenCalledWith('demo')
    expect(searchAthletesMock).not.toHaveBeenCalled()
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

    const initialData = await publicAthleteRouteModule.clientLoader({
      params: { raceId: 'demo', participantId: 'athlete-1' },
      request: new Request('http://localhost/race/demo/athlete/athlete-1'),
      context: undefined as never,
      serverLoader: async () => undefined,
    })

    render(
      <MemoryRouter initialEntries={['/race/demo/athlete/athlete-1']}>
        <Routes>
          <Route
            path="/race/:raceId/athlete/:participantId"
            element={
              <PublicAthleteTrackingPage
                raceId="demo"
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

    expect(getAthleteTrackingMock).toHaveBeenCalledWith('demo', 'athlete-1')
  })
})
