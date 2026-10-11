import type { 
  Race, 
  Participant, 
  ImportJob,
  Checkpoint, 
  CheckpointRecord,
  PublicAthleteSummary,
  PublicAthleteTracking,
  PublicParticipantTrack,
  HeatCell,
  FavoriteAthlete,
  PublicRaceHub,
  PublicRaceLiveFeed,
  RaceCategory,
  RaceResult,
  RaceResultPage,
  PublicRaceResultPage,
  DashboardStats,
  LiveTrackingData,
  User,
  RfidReader,
  RfidRaceOverview,
  OrganizerApplicationPage,
  OrganizerApplicationReview,
  Registration,
  RegistrationField,
  Team,
  PaymentStatus,
  RegistrationDecision,
  Penalty,
  AppNotification,
  Payment,
  RankingEntry,
  MediaItem,
  RaceAnalyticsSummary,
  Demographics,
  RegistrationPoint,
  PromoCode,
  RoutePoint,
  EmergencyAlert
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

export interface BrowserAuthResponse {
  token?: string | null
  refreshToken?: string | null
  requiresEmailVerification?: boolean
  mfaRequired?: boolean
  mfaEnrollmentRequired?: boolean
  mfaChallengeToken?: string | null
  recoveryCodes?: string[]
  user: User
}

let accessToken: string | null = null
let browserRefreshPromise: Promise<boolean> | null = null

export function setAccessToken(token: string | null) {
  accessToken = token
}

export function getAccessToken() {
  return accessToken
}

function getCookie(name: string) {
  if (typeof document === 'undefined') return undefined
  const prefix = `${name}=`
  return document.cookie.split(';').map((part) => part.trim()).find((part) => part.startsWith(prefix))?.slice(prefix.length)
}

async function refreshBrowserSession() {
  if (browserRefreshPromise) return browserRefreshPromise
  browserRefreshPromise = (async () => {
    const csrfToken = getCookie('rt_browser_csrf')
    if (!csrfToken) return false
    const response = await fetch(`${API_BASE}/auth/browser/refresh`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'X-CSRF-Token': decodeURIComponent(csrfToken) },
    })
    if (!response.ok) return false
    const session = await response.json() as BrowserAuthResponse
    if (!session.token) return false
    setAccessToken(session.token)
    return true
  })()
  try {
    return await browserRefreshPromise
  } finally {
    browserRefreshPromise = null
  }
}

async function fetchApi<T>(endpoint: string, options?: RequestInit, allowRefresh = true): Promise<T> {
  const response = await fetch(`${API_BASE}${endpoint}`, {
    ...options,
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...(accessToken && { Authorization: `Bearer ${accessToken}` }),
      ...options?.headers,
    },
  })

  if (response.status === 401 && allowRefresh && !endpoint.startsWith('/auth/')) {
    try {
      if (await refreshBrowserSession()) return fetchApi<T>(endpoint, options, false)
    } catch {
      setAccessToken(null)
    }
  }

  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: 'An error occurred' }))
    throw new ApiError(response.status, error.message)
  }

  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
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

async function downloadExport(path: string): Promise<Blob> {
  const response = await fetch(`${API_BASE}${path}`, {
    credentials: 'include',
    headers: {
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
    },
  })

  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: 'Export failed' }))
    throw new ApiError(response.status, error.message)
  }

  return response.blob()
}

// Auth API
export const authApi = {
  login: (email: string, password: string) =>
    fetchApi<BrowserAuthResponse>('/auth/browser/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    }),

  loginWithGoogle: (idToken: string) =>
    fetchApi<BrowserAuthResponse>(buildContractPath(API_CONTRACT.endpoints.authOAuthGoogle, {}), {
      method: 'POST',
      body: JSON.stringify({ idToken, provider: 'google' }),
    }),

  beginMfaEnrollment: (challengeToken: string) =>
    fetchApi<{ secret: string; provisioningUri: string }>('/auth/browser/mfa/totp/enroll', {
      method: 'POST',
      body: JSON.stringify({ challengeToken }),
    }),

  confirmMfaEnrollment: (challengeToken: string, code: string) =>
    fetchApi<BrowserAuthResponse>('/auth/browser/mfa/totp/confirm', {
      method: 'POST',
      body: JSON.stringify({ challengeToken, code }),
    }),

  verifyMfaLogin: (challengeToken: string, code: string) =>
    fetchApi<BrowserAuthResponse>('/auth/browser/mfa/verify-login', {
      method: 'POST',
      body: JSON.stringify({ challengeToken, code }),
    }),

  restoreBrowserSession: refreshBrowserSession,
  setAccessToken,
  
  register: (data: { email: string; password: string; firstName: string; lastName: string; phone?: string }) =>
    fetchApi<BrowserAuthResponse>('/auth/register', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  registerOrganizer: (data: { email: string; password: string; firstName: string; lastName: string; phone?: string; organizationName: string }) =>
    fetchApi<BrowserAuthResponse>(API_CONTRACT.endpoints.authRegisterOrganizer, {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  
  me: () => fetchApi<User>('/auth/me'),

  updateProfile: (data: { firstName: string; lastName: string; phone?: string }) =>
    fetchApi<User>('/auth/profile', {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  forgotPassword: (email: string) =>
    fetchApi<{ message: string }>('/auth/forgot-password', {
      method: 'POST',
      body: JSON.stringify({ email }),
    }),
  
  logout: async () => {
    const csrfToken = getCookie('rt_browser_csrf')
    try {
      await fetch(`${API_BASE}/auth/browser/logout`, {
        method: 'POST',
        credentials: 'include',
        headers: csrfToken ? { 'X-CSRF-Token': decodeURIComponent(csrfToken) } : {},
      })
    } finally {
      setAccessToken(null)
    }
  },
}

export const organizerApplicationsApi = {
  list: (status?: string, page = 0, size = 20) => {
    const query = new URLSearchParams({ page: String(page), size: String(size) })
    if (status) query.set('status', status)
    return fetchApi<OrganizerApplicationPage>(`/admin/organizer-applications?${query.toString()}`)
  },
  decide: (applicationId: string, decision: 'APPROVE' | 'REJECT', reviewNotes?: string) =>
    fetchApi<OrganizerApplicationReview>(`/admin/organizer-applications/${applicationId}/decision`, {
      method: 'POST',
      body: JSON.stringify({ decision, reviewNotes }),
    }),
}

// Races API
export const racesApi = {
  list: async (includeArchived = false) => {
    const data = await fetchApi<PageResponse<Race>>(`/races${includeArchived ? '?includeArchived=true' : ''}`)
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
  
  clone: (id: string, data: { name: string; raceDate?: string; startTime?: string; copyCategories?: boolean; copyCheckpoints?: boolean; copyRegistrationFields?: boolean }) =>
    fetchApi<Race>(`/races/${id}/clone`, {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  
  archive: (id: string) =>
    fetchApi<Race>(`/races/${id}/archive`, { method: 'POST' }),
  
  restore: (id: string) =>
    fetchApi<Race>(`/races/${id}/restore`, { method: 'POST' }),
  
  updateStatus: (id: string, status: Race['status']) =>
    fetchApi<Race>(`/races/${id}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status }),
    }),
}

// Participants API
export const participantsApi = {
  list: (raceId: string, status?: string) =>
    fetchApi<Participant[]>(`/races/${raceId}/participants${status ? `?status=${encodeURIComponent(status)}` : ''}`),
  
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
  
  listPenalties: (raceId: string, id: string) =>
    fetchApi<Penalty[]>(`/races/${raceId}/participants/${id}/penalties`),
  
  addPenalty: (raceId: string, id: string, data: { seconds: number; reason?: string }) =>
    fetchApi<Penalty>(`/races/${raceId}/participants/${id}/penalties`, {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  
  removePenalty: (raceId: string, id: string, penaltyId: string) =>
    fetchApi<void>(`/races/${raceId}/participants/${id}/penalties/${penaltyId}`, { method: 'DELETE' }),
  
  startCheckout: (raceId: string, id: string, promoCode?: string) =>
    fetchApi<Payment>(`/races/${raceId}/participants/${id}/payment/checkout${promoCode ? `?promoCode=${encodeURIComponent(promoCode)}` : ''}`, { method: 'POST' }),
  
  refundPayment: (raceId: string, id: string) =>
    fetchApi<Payment>(`/races/${raceId}/participants/${id}/payment/refund`, { method: 'POST' }),
  
  importCsv: (raceId: string, file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return fetch(`${API_BASE}/races/${raceId}/participants/import`, {
      method: 'POST',
      headers: {
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      },
      credentials: 'include',
      body: formData,
    }).then(res => res.json())
  },
  
  exportCsv: (raceId: string) =>
    fetch(`${API_BASE}/races/${raceId}/participants/export`, {
      credentials: 'include',
      headers: {
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      },
    }).then(res => res.blob()),

  listImportJobs: (raceId: string) => fetchApi<ImportJob[]>(`/races/${raceId}/participants/import-jobs`),
  
  getImportJob: (raceId: string, jobId: string) => fetchApi<ImportJob>(`/races/${raceId}/participants/import-jobs/${jobId}`),

  downloadBib: (raceId: string, participantId: string) =>
    fetch(`${API_BASE}/races/${raceId}/bibs/${participantId}`, {
      credentials: 'include',
      headers: {
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      },
    }).then(res => res.blob()),

  downloadBatchBibs: (raceId: string, participantIds: string[]) =>
    fetch(`${API_BASE}/races/${raceId}/bibs/batch`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      },
      body: JSON.stringify(participantIds),
    }).then(res => res.blob()),

  review: (raceId: string, id: string, decision: RegistrationDecision, reason?: string) =>
    fetchApi<Participant>(`/races/${raceId}/participants/${id}/review`, {
      method: 'POST',
      body: JSON.stringify({ decision, reason }),
    }),

  verifyPayment: (raceId: string, id: string, data: { status: PaymentStatus; reference?: string; amount?: number }) =>
    fetchApi<Participant>(`/races/${raceId}/participants/${id}/payment`, {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  myRegistrations: () => fetchApi<Registration[]>('/participants/my-registrations'),
}

// Registration fields / teams API
export const registrationApi = {
  listFields: (raceId: string) => fetchApi<RegistrationField[]>(`/races/${raceId}/registration-fields`),

  createField: (raceId: string, data: Partial<RegistrationField>) =>
    fetchApi<RegistrationField>(`/races/${raceId}/registration-fields`, {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  updateField: (raceId: string, id: string, data: Partial<RegistrationField>) =>
    fetchApi<RegistrationField>(`/races/${raceId}/registration-fields/${id}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  deleteField: (raceId: string, id: string) =>
    fetchApi<void>(`/races/${raceId}/registration-fields/${id}`, { method: 'DELETE' }),

  listTeams: (raceId: string) => fetchApi<Team[]>(`/races/${raceId}/teams`),

  createTeam: (raceId: string, data: { name: string; clubName?: string }) =>
    fetchApi<Team>(`/races/${raceId}/teams`, {
      method: 'POST',
      body: JSON.stringify(data),
    }),
}

export interface PublicRegistrationPayload {
  categoryId: string
  emergencyContact: { name?: string; phone?: string; relationship?: string }
  waiverAccepted?: boolean
  shirtSize?: string
  teamId?: string
  clubName?: string
  teamName?: string
  answers?: Array<{ fieldId: string; value?: string }>
}

export const publicRegistrationApi = {
  listFields: (raceId: string) => fetchApi<RegistrationField[]>(`/public/races/${raceId}/registration-fields`),

  register: (raceId: string, payload: PublicRegistrationPayload) =>
    fetchApi<Registration>(`/races/${raceId}/participants/register`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
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

  correctRecord: (raceId: string, checkpointId: string, recordId: string, data: { adjustedRecordedAt?: string; reason?: string; verified?: boolean }) =>
    fetchApi<CheckpointRecord>(`/races/${raceId}/checkpoints/${checkpointId}/records/${recordId}`, {
      method: 'PATCH',
      body: JSON.stringify(data),
    }),

  exportCheckpoints: (raceId: string, format: 'csv' | 'xlsx') =>
    downloadExport(buildContractPath(API_CONTRACT.endpoints.raceCheckpointExport, { raceId }, { format })),
}

export const rfidApi = {
  listReaders: (raceId: string) => fetchApi<RfidReader[]>(`/races/${raceId}/rfid/readers`),
  getOverview: (raceId: string) => fetchApi<RfidRaceOverview>(`/races/${raceId}/rfid/overview`),
  revokeReader: (raceId: string, readerId: string) =>
    fetchApi<RfidReader>(`/races/${raceId}/rfid/readers/${readerId}/revoke`, {
      method: 'PATCH',
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

  page: (raceId: string, page = 0, size = 50, revision?: number) => {
    return fetchApi<RaceResultPage>(buildContractPath(
      API_CONTRACT.endpoints.raceResultsPage,
      { raceId },
      { page, size, revision },
    ))
  },

  publicPage: (raceId: string, page = 0, size = 25, revision?: number) => {
    return fetchPublicApi<PublicRaceResultPage>(buildContractPath(
      API_CONTRACT.endpoints.publicRaceResults,
      { raceId },
      { page, size, revision },
    ))
  },

  my: () => fetchApi<RaceResult[]>('/results/my'),
  
  publish: (raceId: string) =>
    fetchApi<void>(`/races/${raceId}/results/publish`, { method: 'POST' }),

  teamRankings: (raceId: string) =>
    fetchApi<RankingEntry[]>(`/races/${raceId}/rankings/teams`),

  countryRankings: (raceId: string) =>
    fetchApi<RankingEntry[]>(`/races/${raceId}/rankings/countries`),

  exportResults: (raceId: string, format: 'csv' | 'pdf' | 'xlsx') =>
    downloadExport(buildContractPath(API_CONTRACT.endpoints.raceResultsExport, { raceId }, { format })),

  publicCertificateUrl: (raceId: string, participantId: string, token: string) =>
    `${API_BASE}${buildContractPath(API_CONTRACT.endpoints.publicRaceResultCertificate, { raceId, participantId })}?token=${encodeURIComponent(token)}`,
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

  getLiveFeed: (raceId: string, options?: { cursor?: string; limit?: number; query?: string }) =>
    fetchPublicApi<PublicRaceLiveFeed>(
      buildContractPath(API_CONTRACT.endpoints.publicRaceLiveFeed, { raceId }, {
        cursor: options?.cursor,
        limit: options?.limit,
        q: options?.query,
      }),
    ),

  searchAthletes: (raceId: string, query: string) =>
    fetchPublicApi<PublicAthleteSummary[]>(
      buildContractPath(API_CONTRACT.endpoints.publicRaceSearch, { raceId }, { q: query }),
    ),

  getAthleteTracking: (raceId: string, participantId: string) =>
    fetchPublicApi<PublicAthleteTracking>(
      buildContractPath(API_CONTRACT.endpoints.publicAthleteTracking, { raceId, participantId }),
    ),

  getAthleteTrack: (raceId: string, participantId: string) =>
    fetchPublicApi<PublicParticipantTrack>(
      buildContractPath(API_CONTRACT.endpoints.publicAthleteTrack, { raceId, participantId }),
    ),

  getHeatmap: (raceId: string, options?: { from?: string; to?: string; scale?: number }) =>
    fetchPublicApi<HeatCell[]>(
      buildContractPath(API_CONTRACT.endpoints.publicRaceHeatmap, { raceId }, options),
    ),

  getRoute: (raceId: string) =>
    fetchPublicApi<RoutePoint[]>(buildContractPath(API_CONTRACT.endpoints.publicRaceRoute, { raceId })),
}

export const favoritesApi = {
  list: () => fetchApi<FavoriteAthlete[]>('/favorites'),

  follow: (raceId: string, participantId: string) =>
    fetchApi<FavoriteAthlete>('/favorites', {
      method: 'POST',
      body: JSON.stringify({ raceId, participantId }),
    }),

  unfollow: (raceId: string, participantId: string) =>
    fetchApi<void>(`/favorites/${participantId}?raceId=${encodeURIComponent(raceId)}`, { method: 'DELETE' }),
}

export const notificationsApi = {
  list: () => fetchApi<AppNotification[]>(API_CONTRACT.endpoints.notifications),

  markRead: (id: string) =>
    fetchApi<AppNotification>(`${API_CONTRACT.endpoints.notifications}/${id}/read`, { method: 'PUT' }),

  registerDevice: (token: string, platform = 'web') =>
    fetchApi<{ status: string }>(API_CONTRACT.endpoints.notificationDevices, {
      method: 'POST',
      body: JSON.stringify({ token, platform }),
    }),

  unregisterDevice: (token: string) =>
    fetchApi<void>(`${API_CONTRACT.endpoints.notificationDevices}?token=${encodeURIComponent(token)}`, {
      method: 'DELETE',
    }),
}

export const mediaApi = {
  list: (raceId: string) => fetchApi<MediaItem[]>(`/races/${raceId}/media`),

  publicList: (raceId: string) => fetchPublicApi<MediaItem[]>(`/public/races/${raceId}/media`),

  upload: async (raceId: string, file: File, caption?: string, participantId?: string) => {
    const formData = new FormData()
    formData.append('file', file)
    if (caption) formData.append('caption', caption)
    if (participantId) formData.append('participantId', participantId)
    const response = await fetch(`${API_BASE}/races/${raceId}/media`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      },
      body: formData,
    })
    if (!response.ok) throw new Error('Failed to upload media')
    return response.json() as Promise<MediaItem>
  },

  approve: (raceId: string, id: string) =>
    fetchApi<MediaItem>(`/races/${raceId}/media/${id}/approve`, { method: 'POST' }),

  reject: (raceId: string, id: string) =>
    fetchApi<MediaItem>(`/races/${raceId}/media/${id}/reject`, { method: 'POST' }),

  remove: (raceId: string, id: string) =>
    fetchApi<void>(`/races/${raceId}/media/${id}`, { method: 'DELETE' }),
}

export const analyticsApi = {
  summary: (raceId: string) => fetchApi<RaceAnalyticsSummary>(`/races/${raceId}/analytics/summary`),
  demographics: (raceId: string) => fetchApi<Demographics>(`/races/${raceId}/analytics/demographics`),
  registrations: (raceId: string, days = 30) =>
    fetchApi<RegistrationPoint[]>(`/races/${raceId}/analytics/registrations?days=${days}`),

  exportAnalytics: (raceId: string, format: 'csv' | 'xlsx') =>
    downloadExport(buildContractPath(API_CONTRACT.endpoints.raceAnalyticsExport, { raceId }, { format })),
}

export const routeApi = {
  get: (raceId: string) =>
    fetchApi<RoutePoint[]>(buildContractPath(API_CONTRACT.endpoints.raceRoute, { raceId })),

  importGpx: async (raceId: string, file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    const response = await fetch(`${API_BASE}${buildContractPath(API_CONTRACT.endpoints.raceRouteGpx, { raceId })}`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      },
      body: formData,
    })
    if (!response.ok) throw new Error('Failed to import GPX route')
    return response.json() as Promise<RoutePoint[]>
  },
}

export const emergencyApi = {
  raise: (raceId: string, data: { latitude: number; longitude: number; message?: string }) =>
    fetchApi<EmergencyAlert>(buildContractPath(API_CONTRACT.endpoints.raceEmergency, { raceId }), {
      method: 'POST',
      body: JSON.stringify(data),
    }),
}

export const promoApi = {
  list: (raceId: string) => fetchApi<PromoCode[]>(`/races/${raceId}/promo-codes`),

  create: (raceId: string, data: { code: string; discountType: 'PERCENT' | 'FIXED'; discountValue: number; maxUses?: number; expiresAt?: string }) =>
    fetchApi<PromoCode>(`/races/${raceId}/promo-codes`, { method: 'POST', body: JSON.stringify(data) }),

  setActive: (raceId: string, id: string, active: boolean) =>
    fetchApi<PromoCode>(`/races/${raceId}/promo-codes/${id}/active`, {
      method: 'PATCH',
      body: JSON.stringify({ active }),
    }),

  remove: (raceId: string, id: string) =>
    fetchApi<void>(`/races/${raceId}/promo-codes/${id}`, { method: 'DELETE' }),
}

import { Client } from '@stomp/stompjs'

// STOMP client management
let stompClient: Client | null = null

export function connectLiveTracking(raceId: string, onMessage: (data: LiveTrackingData) => void) {
  const envUrl = (import.meta.env.VITE_WS_URL as string | undefined)?.trim()
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  // Served by the backend at the API context path and proxied by the web server.
  const wsUrl = envUrl || `${protocol}//${window.location.host}${API_CONTRACT.web.wsTrackingPath}`
  
  const token = accessToken
  
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

export interface UserAlertPayload {
  title: string
  message: string
  type?: string
}

let userAlertClient: Client | null = null

export function connectUserAlerts(userId: string, onMessage: (data: UserAlertPayload) => void) {
  const envUrl = (import.meta.env.VITE_WS_URL as string | undefined)?.trim()
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const wsUrl = envUrl || `${protocol}//${window.location.host}${API_CONTRACT.web.wsTrackingPath}`

  userAlertClient = new Client({
    brokerURL: wsUrl,
    connectHeaders: { Authorization: `Bearer ${accessToken}` },
    reconnectDelay: 5000,
    heartbeatIncoming: 4000,
    heartbeatOutgoing: 4000,
  })

  userAlertClient.onConnect = () => {
    userAlertClient?.subscribe(`/topic/user.${userId}`, (message) => {
      if (message.body) {
        onMessage(JSON.parse(message.body))
      }
    })
  }

  userAlertClient.activate()

  return () => {
    if (userAlertClient) {
      userAlertClient.deactivate()
      userAlertClient = null
    }
  }
}
