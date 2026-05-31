import type { 
  Race, 
  Participant, 
  Checkpoint, 
  CheckpointRecord,
  PublicAthleteSummary,
  PublicAthleteTracking,
  PublicRaceHub,
  RaceCategory,
  RaceResult,
  DashboardStats,
  LiveTrackingData,
  User
} from '@/types'
import { API_CONTRACT } from '@race-tracker/contracts'

const API_BASE = API_CONTRACT.web.apiBasePath

class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message)
    this.name = 'ApiError'
  }
}

interface PageResponse<T> {
  content: T[]
}

interface AuthApiResponse {
  token?: string | null
  refreshToken?: string | null
  requiresEmailVerification?: boolean
  user: User
}

interface LoginApiResponse {
  token: string
  refreshToken: string
  user: User
}

async function fetchApi<T>(endpoint: string, options?: RequestInit): Promise<T> {
  const token = localStorage.getItem('auth_token')
  
  const response = await fetch(`${API_BASE}${endpoint}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token && { Authorization: `Bearer ${token}` }),
      ...options?.headers,
    },
  })

  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: 'An error occurred' }))
    throw new ApiError(response.status, error.message)
  }

  return response.json()
}

function buildContractPath(
  template: string,
  params: Record<string, string | number>,
  query?: Record<string, string | number | null | undefined>,
) {
  const path = Object.entries(params).reduce(
    (value, [key, replacement]) => value.replace(`:${key}`, encodeURIComponent(String(replacement))),
    template,
  )

  const searchParams = new URLSearchParams()
  for (const [key, value] of Object.entries(query ?? {})) {
    if (value !== null && value !== undefined && String(value).length > 0) {
      searchParams.set(key, String(value))
    }
  }

  const search = searchParams.toString()
  return search ? `${path}?${search}` : path
}

async function fetchPublicApi<T>(endpoint: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${endpoint}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
  })

  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: 'An error occurred' }))
    throw new ApiError(response.status, error.message)
  }

  return response.json()
}

// Auth API
export const authApi = {
  login: (email: string, password: string) =>
    fetchApi<LoginApiResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    }),
  
  register: (data: { email: string; password: string; firstName: string; lastName: string; phone?: string }) =>
    fetchApi<AuthApiResponse>('/auth/register', {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  
  me: () => fetchApi<User>('/auth/me'),
  
  logout: () => {
    localStorage.removeItem('auth_token')
  },
}

// Races API
export const racesApi = {
  list: async () => {
    const data = await fetchApi<PageResponse<Race>>('/races')
    return data.content ?? []
  },
  
  get: (id: string) => fetchApi<Race>(`/races/${id}`),
  
  create: (data: Partial<Race>) =>
    fetchApi<Race>('/races', {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  
  update: (id: string, data: Partial<Race>) =>
    fetchApi<Race>(`/races/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
  
  delete: (id: string) =>
    fetchApi<void>(`/races/${id}`, { method: 'DELETE' }),
  
  updateStatus: (id: string, status: Race['status']) =>
    fetchApi<Race>(`/races/${id}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status }),
    }),
}

// Participants API
export const participantsApi = {
  list: (raceId: string) => fetchApi<Participant[]>(`/races/${raceId}/participants`),
  
  get: (raceId: string, id: string) => fetchApi<Participant>(`/races/${raceId}/participants/${id}`),
  
  create: (raceId: string, data: Partial<Participant>) =>
    fetchApi<Participant>(`/races/${raceId}/participants`, {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  
  update: (raceId: string, id: string, data: Partial<Participant>) =>
    fetchApi<Participant>(`/races/${raceId}/participants/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
  
  delete: (raceId: string, id: string) =>
    fetchApi<void>(`/races/${raceId}/participants/${id}`, { method: 'DELETE' }),
  
  checkIn: (raceId: string, id: string) =>
    fetchApi<Participant>(`/races/${raceId}/participants/${id}/check-in`, { method: 'POST' }),
  
  importCsv: (raceId: string, file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return fetch(`${API_BASE}/races/${raceId}/participants/import`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${localStorage.getItem('auth_token')}`,
      },
      body: formData,
    }).then(res => res.json())
  },
  
  exportCsv: (raceId: string) =>
    fetch(`${API_BASE}/races/${raceId}/participants/export`, {
      headers: {
        Authorization: `Bearer ${localStorage.getItem('auth_token')}`,
      },
    }).then(res => res.blob()),

  listImportJobs: (raceId: string) => fetchApi<any[]>(`/races/${raceId}/participants/import-jobs`),
  
  getImportJob: (raceId: string, jobId: string) => fetchApi<any>(`/races/${raceId}/participants/import-jobs/${jobId}`),

  downloadBib: (raceId: string, participantId: string) =>
    fetch(`${API_BASE}/races/${raceId}/bibs/${participantId}`, {
      headers: {
        Authorization: `Bearer ${localStorage.getItem('auth_token')}`,
      },
    }).then(res => res.blob()),

  downloadBatchBibs: (raceId: string, participantIds: string[]) =>
    fetch(`${API_BASE}/races/${raceId}/bibs/batch`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${localStorage.getItem('auth_token')}`,
      },
      body: JSON.stringify(participantIds),
    }).then(res => res.blob()),

  myRegistrations: () => fetchApi<Array<{ raceId: string; status: string }>>('/participants/my-registrations'),
}

// Checkpoints API
export const checkpointsApi = {
  list: (raceId: string) => fetchApi<Checkpoint[]>(`/races/${raceId}/checkpoints`),
  
  create: (raceId: string, data: Partial<Checkpoint>) =>
    fetchApi<Checkpoint>(`/races/${raceId}/checkpoints`, {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  
  update: (raceId: string, id: string, data: Partial<Checkpoint>) =>
    fetchApi<Checkpoint>(`/races/${raceId}/checkpoints/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
  
  delete: (raceId: string, id: string) =>
    fetchApi<void>(`/races/${raceId}/checkpoints/${id}`, { method: 'DELETE' }),
  
  recordTime: (raceId: string, checkpointId: string, data: { participantId: string; method: string }) =>
    fetchApi<CheckpointRecord>(`/races/${raceId}/checkpoints/${checkpointId}/record`, {
      method: 'POST',
      body: JSON.stringify(data),
    }),
}

// Categories API
export const categoriesApi = {
  list: (raceId: string) => fetchApi<RaceCategory[]>(`/races/${raceId}/categories`),
  
  create: (raceId: string, data: Partial<RaceCategory>) =>
    fetchApi<RaceCategory>(`/races/${raceId}/categories`, {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  
  update: (raceId: string, id: string, data: Partial<RaceCategory>) =>
    fetchApi<RaceCategory>(`/races/${raceId}/categories/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
  
  delete: (raceId: string, id: string) =>
    fetchApi<void>(`/races/${raceId}/categories/${id}`, { method: 'DELETE' }),
}

// Results API
export const resultsApi = {
  list: (raceId: string) => fetchApi<RaceResult[]>(`/races/${raceId}/results`),

  my: () => fetchApi<RaceResult[]>('/results/my'),
  
  publish: (raceId: string) =>
    fetchApi<void>(`/races/${raceId}/results/publish`, { method: 'POST' }),
}

// Tracking API
export const trackingApi = {
  getLiveData: (raceId: string) => fetchApi<LiveTrackingData[]>(`/races/${raceId}/tracking/live`),
  
  getParticipantTrack: (raceId: string, participantId: string) =>
    fetchApi<{ locations: Array<{ lat: number; lng: number; time: string }> }>(
      `/races/${raceId}/tracking/${participantId}`
    ),
}

// Dashboard API
export const dashboardApi = {
  getStats: () => fetchApi<DashboardStats>('/dashboard/stats'),
}

export const publicTrackingApi = {
  getRaceHub: (raceId: string) =>
    fetchPublicApi<PublicRaceHub>(
      buildContractPath(API_CONTRACT.endpoints.publicRaceHub, { raceId }),
    ),

  getLiveAthletes: (raceId: string) =>
    fetchPublicApi<PublicAthleteSummary[]>(
      buildContractPath(API_CONTRACT.endpoints.publicRaceLive, { raceId }),
    ),

  searchAthletes: (raceId: string, query: string) =>
    fetchPublicApi<PublicAthleteSummary[]>(
      buildContractPath(API_CONTRACT.endpoints.publicRaceSearch, { raceId }, { q: query }),
    ),

  getAthleteTracking: (raceId: string, participantId: string) =>
    fetchPublicApi<PublicAthleteTracking>(
      buildContractPath(API_CONTRACT.endpoints.publicAthleteTracking, { raceId, participantId }),
    ),
}

import { Client } from '@stomp/stompjs'

// STOMP client management
let stompClient: Client | null = null

export function connectLiveTracking(raceId: string, onMessage: (data: LiveTrackingData) => void) {
  const envUrl = (import.meta.env.VITE_WS_URL as string | undefined)?.trim()
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  // The backend endpoint is /ws-tracking
  const wsUrl = envUrl || `${protocol}//${window.location.host}/ws-tracking`
  
  const token = localStorage.getItem('auth_token')
  
  stompClient = new Client({
    brokerURL: wsUrl,
    connectHeaders: {
      Authorization: `Bearer ${token}`,
    },
    debug: (str) => {
      console.log('STOMP Debug:', str)
    },
    reconnectDelay: 5000,
    heartbeatIncoming: 4000,
    heartbeatOutgoing: 4000,
  })

  stompClient.onConnect = () => {
    console.log('STOMP Connected to race:', raceId)
    stompClient?.subscribe(`/topic/race.${raceId}`, (message) => {
      if (message.body) {
        onMessage(JSON.parse(message.body))
      }
    })
  }

  stompClient.onStompError = (frame) => {
    console.error('Broker reported error: ' + frame.headers['message'])
    console.error('Additional details: ' + frame.body)
  }

  stompClient.activate()
  
  return () => {
    if (stompClient) {
      stompClient.deactivate()
      stompClient = null
    }
  }
}
