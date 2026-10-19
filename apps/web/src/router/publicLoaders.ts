import { publicTrackingApi } from '@/lib/api'
import type { PublicAthleteSummary, PublicAthleteTracking, PublicRaceHub } from '@/types'

export interface PublicRaceHubLoaderData {
  raceId: string
  hub: PublicRaceHub
  liveAthletes: PublicAthleteSummary[]
}

export interface PublicAthleteTrackingLoaderData {
  raceId: string
  participantId: string
  tracking: PublicAthleteTracking
}

export const PUBLIC_BROWSER_DEMO_RACE_ID = 'demo'

const BROWSER_DEMO_HUB: PublicRaceHub = {
  raceId: PUBLIC_BROWSER_DEMO_RACE_ID,
  raceName: 'Race Tracker Browser Demo',
  status: 'IN_PROGRESS',
  raceDate: '2026-05-31T06:00:00.000Z',
  city: 'Harare',
  country: 'Zimbabwe',
  distanceKm: 42.2,
  leaderboard: [],
}

export async function loadPublicRaceHubData(raceId: string): Promise<PublicRaceHubLoaderData> {
  if (raceId === PUBLIC_BROWSER_DEMO_RACE_ID) {
    return {
      raceId,
      hub: BROWSER_DEMO_HUB,
      liveAthletes: [],
    }
  }

  const [hub, liveAthletes] = await Promise.all([
    publicTrackingApi.getRaceHub(raceId),
    publicTrackingApi.getLiveFeed(raceId, { limit: 100 }),
  ])

  return {
    raceId,
    hub,
    liveAthletes: liveAthletes.items,
  }
}

export async function loadPublicAthleteTrackingData(
  raceId: string,
  participantId: string,
): Promise<PublicAthleteTrackingLoaderData> {
  const tracking = await publicTrackingApi.getAthleteTracking(raceId, participantId)

  return {
    raceId,
    participantId,
    tracking,
  }
}
