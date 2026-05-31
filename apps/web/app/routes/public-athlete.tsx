import { useLoaderData } from 'react-router-dom'
import type { Route } from './+types/public-athlete'
import { PublicAthleteTrackingPage } from '@/pages/PublicAthleteTrackingPage'
import { loadPublicAthleteTrackingData } from '@/router/publicLoaders'

export async function clientLoader({ params }: Route.ClientLoaderArgs) {
  if (!params.raceId || !params.participantId) {
    throw new Response('Athlete tracking not found', { status: 404 })
  }

  return loadPublicAthleteTrackingData(params.raceId, params.participantId)
}

clientLoader.hydrate = true as const

export default function PublicAthleteRoute() {
  const initialData = useLoaderData() as Awaited<ReturnType<typeof clientLoader>> | undefined

  return (
    <PublicAthleteTrackingPage
      raceId={initialData?.raceId}
      participantId={initialData?.participantId}
      initialData={initialData}
    />
  )
}
