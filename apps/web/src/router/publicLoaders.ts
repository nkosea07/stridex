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

export async function loadPublicRaceHubData(raceId: string): Promise<PublicRaceHubLoaderData> {
  const [hub, liveAthletes] = await Promise.all([
    publicTrackingApi.getRaceHub(raceId),
    publicTrackingApi.getLiveAthletes(raceId),
  ])

  return {
    raceId,
    hub,
    liveAthletes,
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
