import { useLoaderData } from 'react-router-dom'
import type { Route } from './+types/public-race'
import { PublicRaceHubPage } from '@/pages/PublicRaceHubPage'
import { loadPublicRaceHubData } from '@/router/publicLoaders'

export async function clientLoader({ params }: Route.ClientLoaderArgs) {
  if (!params.raceId) {
    throw new Response('Race not found', { status: 404 })
  }

  return loadPublicRaceHubData(params.raceId)
}

clientLoader.hydrate = true as const

export default function PublicRaceRoute() {
  const initialData = useLoaderData() as Awaited<ReturnType<typeof clientLoader>> | undefined

  return <PublicRaceHubPage raceId={initialData?.raceId} initialData={initialData} />
}
