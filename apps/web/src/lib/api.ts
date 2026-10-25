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
  EmergencyAlert,
  DeletionStatus,
  NotificationPreference,
  ConsentState,
  RaceInsightsSummary,
  ParticipantAnomaly,
  RfidReaderCreateResponse,
  UserCapability,
} from '@/types'
import { API_CONTRACT } from '@race-tracker/contracts'

const API_BASE = API_CONTRACT.web.apiBasePath
const PUBLIC_AUTH_ENDPOINTS = new Set([
  API_CONTRACT.endpoints.authLogin,
  API_CONTRACT.endpoints.authDeletionCancel,
  API_CONTRACT.endpoints.authRegister,
  API_CONTRACT.endpoints.authRegisterOrganizer,
  API_CONTRACT.endpoints.authOAuthGoogle,
  API_CONTRACT.endpoints.authForgotPassword,
  API_CONTRACT.endpoints.authResetPassword,
  API_CONTRACT.endpoints.authVerifyEmail,
  API_CONTRACT.endpoints.authResendVerification,
  API_CONTRACT.endpoints.authMfaVerifyLogin,
  API_CONTRACT.endpoints.authBrowserLogin,
  API_CONTRACT.endpoints.authBrowserMfaVerifyLogin,
  API_CONTRACT.endpoints.authRefresh,
  API_CONTRACT.endpoints.authBrowserRefresh,
])

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public code?: string,
    public details: string[] = [],
  ) {
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
  user: User
}

interface ApiErrorPayload {
  message?: unknown
  code?: unknown
  details?: unknown
}

function isUserCapability(value: unknown): value is UserCapability {
  return value === 'ATHLETE' || value === 'SPECTATOR'
}

export function normalizeUser(user: User): User {
  const candidate = user as User & { capabilities?: unknown; preferredCapability?: unknown }
  const hasCapabilitiesField = candidate.capabilities !== undefined
  const capabilities = Array.from(new Set(
    Array.isArray(candidate.capabilities) ? candidate.capabilities.filter(isUserCapability) : [],
  ))
  const legacyCapability = isUserCapability(user.role) ? user.role : undefined
  const normalizedCapabilities = hasCapabilitiesField ? capabilities : legacyCapability ? [legacyCapability] : []
  const preferredCapability = isUserCapability(candidate.preferredCapability)
    && normalizedCapabilities.includes(candidate.preferredCapability)
    ? candidate.preferredCapability
    : normalizedCapabilities[0] ?? null

  return {
    ...user,
    capabilities: normalizedCapabilities,
    preferredCapability,
  }
}

function normalizeAuthResponse(response: BrowserAuthResponse): BrowserAuthResponse {
  return { ...response, user: normalizeUser(response.user) }
}

async function getApiError(response: Response, fallbackMessage: string) {
  const rawPayload = await response.json().catch(() => ({}))
  const payload = rawPayload && typeof rawPayload === 'object' ? rawPayload as ApiErrorPayload : {}
  const details = Array.isArray(payload.details)
    ? payload.details.filter((detail): detail is string => typeof detail === 'string')
    : []
  const message = typeof payload.message === 'string' ? payload.message : fallbackMessage
  const code = typeof payload.code === 'string' ? payload.code : undefined
  return new ApiError(response.status, message, code, details)
}

let accessToken: string | null = null
let browserRefreshPromise: { generation: number; promise: Promise<boolean> } | null = null
let browserSessionGeneration = 0

export function setAccessToken(token: string | null) {
  accessToken = token
}

export function getAccessToken() {
  return accessToken
}

function invalidateBrowserSession() {
  browserSessionGeneration += 1
  setAccessToken(null)
}

function getCookie(name: string) {
  if (typeof document === 'undefined') return undefined
  const prefix = `${name}=`
  return document.cookie.split(';').map((part) => part.trim()).find((part) => part.startsWith(prefix))?.slice(prefix.length)
}

async function refreshBrowserSession(expectedGeneration = browserSessionGeneration) {
  if (expectedGeneration !== browserSessionGeneration) return false
  if (browserRefreshPromise?.generation === expectedGeneration) return browserRefreshPromise.promise
  const refreshGeneration = expectedGeneration
  setAccessToken(null)
  const promise = (async () => {
    const csrfToken = getCookie('rt_browser_csrf')
    if (!csrfToken) return false
    try {
      const response = await fetch(`${API_BASE}${API_CONTRACT.endpoints.authBrowserRefresh}`, {
        method: 'POST',
        credentials: 'include',
        headers: { 'X-CSRF-Token': decodeURIComponent(csrfToken) },
      })
      if (!response.ok) return false
      const session = await response.json() as BrowserAuthResponse
      if (!session.token || refreshGeneration !== browserSessionGeneration) return false
      setAccessToken(session.token)
      return true
    } catch {
      return false
    }
  })()
  browserRefreshPromise = { generation: refreshGeneration, promise }
  try {
    return await promise
  } finally {
    if (browserRefreshPromise?.promise === promise) browserRefreshPromise = null
  }
}

async function fetchAuthenticated(
  endpoint: string,
  options?: RequestInit,
  fallbackMessage = 'An error occurred',
  allowRefresh = true,
  requestGeneration = browserSessionGeneration,
): Promise<Response> {
  const response = await fetch(`${API_BASE}${endpoint}`, {
    ...options,
    credentials: 'include',
    headers: {
      ...(accessToken && { Authorization: `Bearer ${accessToken}` }),
      ...options?.headers,
    },
  })

  if (
    response.status === 401
    && allowRefresh
    && !PUBLIC_AUTH_ENDPOINTS.has(endpoint)
    && requestGeneration === browserSessionGeneration
  ) {
    try {
      if (
        await refreshBrowserSession(requestGeneration)
        && requestGeneration === browserSessionGeneration
      ) {
        return fetchAuthenticated(endpoint, options, fallbackMessage, false, requestGeneration)
      }
    } catch {
      if (requestGeneration === browserSessionGeneration) setAccessToken(null)
    }
  }

  if (!response.ok) {
    throw await getApiError(response, fallbackMessage)
  }

  return response
}

async function fetchApi<T>(
  endpoint: string,
  options?: RequestInit,
): Promise<T> {
  const response = await fetchAuthenticated(endpoint, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
  })

  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

function buildContractPath(
  template: string,
  params: Record<string, string | number>,
  query?: Record<string, string | number | null | undefined>,
) {
  const placeholders = [...template.matchAll(/:([A-Za-z][A-Za-z0-9_]*)/g)].map(([, key]) => key)
  const providedKeys = Object.keys(params)
  if (placeholders.length !== providedKeys.length || placeholders.some((key) => !providedKeys.includes(key))) {
    throw new Error(`Contract path parameters do not match template: ${template}`)
  }

  const path = placeholders.reduce(
    (value, key) => value.replace(`:${key}`, encodeURIComponent(String(params[key]))),
    template,
  )
  if (/:[A-Za-z][A-Za-z0-9_]*/.test(path)) {
    throw new Error(`Contract path contains an unresolved parameter: ${template}`)
  }

  const searchParams = new URLSearchParams()
  for (const [key, value] of Object.entries(query ?? {})) {
    if (value !== null && value !== undefined) {
      searchParams.set(key, String(value))
    }
  }

  const search = searchParams.toString()
  return search ? `${path}?${search}` : path
}

async function fetchPublicApi<T>(endpoint: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${endpoint}`, {
    ...options,
    credentials: 'omit',
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
  })

  if (!response.ok) {
    throw await getApiError(response, 'An error occurred')
  }

  return response.json()
}

async function downloadExport(path: string): Promise<Blob> {
  const response = await fetchAuthenticated(path, undefined, 'Export failed')
  return response.blob()
}

// Auth API
export const authApi = {
  login: async (email: string, password: string) => {
    invalidateBrowserSession()
    return normalizeAuthResponse(await fetchApi<BrowserAuthResponse>(API_CONTRACT.endpoints.authBrowserLogin, {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    }))
  },

  loginWithGoogle: async (idToken: string) => {
    invalidateBrowserSession()
    return normalizeAuthResponse(await fetchApi<BrowserAuthResponse>(buildContractPath(API_CONTRACT.endpoints.authOAuthGoogle, {}), {
      method: 'POST',
      body: JSON.stringify({ idToken, provider: 'google' }),
    }))
  },

  restoreBrowserSession: refreshBrowserSession,
  setAccessToken,
  
  register: (data: {
    email: string
    password: string
    firstName: string
    lastName: string
    phone?: string
    privacyPolicyAccepted?: boolean
    privacyPolicyVersion?: string
    experiences?: UserCapability[]
  }) =>
    fetchApi<BrowserAuthResponse>(API_CONTRACT.endpoints.authRegister, {
      method: 'POST',
      body: JSON.stringify(data),
    }).then(normalizeAuthResponse),

  registerOrganizer: (data: { email: string; password: string; firstName: string; lastName: string; phone?: string; organizationName: string; privacyPolicyAccepted: boolean; privacyPolicyVersion: string }) =>
    fetchApi<BrowserAuthResponse>(API_CONTRACT.endpoints.authRegisterOrganizer, {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  
  me: () => fetchApi<User>(API_CONTRACT.endpoints.authMe).then(normalizeUser),

  updateProfile: (data: { firstName: string; lastName: string; phone?: string }) =>
    fetchApi<User>(API_CONTRACT.endpoints.authProfile, {
      method: 'PUT',
      body: JSON.stringify(data),
    }).then(normalizeUser),

  updateCapabilities: (data: { capabilities: UserCapability[]; preferredCapability: UserCapability }) =>
    fetchApi<User>(API_CONTRACT.endpoints.authProfileCapabilities, {
      method: 'PUT',
      body: JSON.stringify(data),
    }).then(normalizeUser),

  deleteAccount: (password: string, confirmation: string) =>
    fetchApi<DeletionStatus>(buildContractPath(API_CONTRACT.endpoints.myAccount, {}), {
      method: 'DELETE',
      body: JSON.stringify({ password, confirmation }),
    }),

  deletionStatus: () =>
    fetchApi<DeletionStatus>(buildContractPath(API_CONTRACT.endpoints.myDeletionStatus, {})),

  cancelDeletion: () =>
    fetchApi<DeletionStatus>(buildContractPath(API_CONTRACT.endpoints.myDeletionCancel, {}), {
      method: 'POST',
    }),

  cancelDeletionWithPassword: (email: string, password: string) =>
    fetchApi<{ message: string }>(API_CONTRACT.endpoints.authDeletionCancel, {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    }),

  exportData: () =>
    fetchApi<Record<string, unknown>>(buildContractPath(API_CONTRACT.endpoints.myDataExport, {})),

  consent: () =>
    fetchApi<ConsentState>(buildContractPath(API_CONTRACT.endpoints.myConsent, {})),

  updateConsent: (data: { dataProcessingConsent?: boolean; marketingConsent?: boolean }) =>
    fetchApi<ConsentState>(buildContractPath(API_CONTRACT.endpoints.myConsent, {}), {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  forgotPassword: (email: string) =>
    fetchApi<{ message: string }>(API_CONTRACT.endpoints.authForgotPassword, {
      method: 'POST',
      body: JSON.stringify({ email }),
    }),
  
  logout: async () => {
    invalidateBrowserSession()
    const csrfToken = getCookie('rt_browser_csrf')
    try {
      await fetch(`${API_BASE}${API_CONTRACT.endpoints.authBrowserLogout}`, {
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
    return fetchApi<OrganizerApplicationPage>(`${API_CONTRACT.endpoints.organizerApplications}?${query.toString()}`)
  },
  decide: (applicationId: string, decision: 'APPROVE' | 'REJECT', reviewNotes?: string) =>
    fetchApi<OrganizerApplicationReview>(buildContractPath(
      API_CONTRACT.endpoints.organizerApplicationDecision,
      { applicationId },
    ), {
      method: 'POST',
      body: JSON.stringify({ decision, reviewNotes }),
    }),
}

// Races API
export const racesApi = {
  list: async (includeArchived = false) => {
    const data = await fetchApi<PageResponse<Race>>(buildContractPath(
      API_CONTRACT.endpoints.racesList,
      {},
      { includeArchived: includeArchived ? 'true' : undefined },
    ))
    return data.content ?? []
  },
  
  get: (id: string) => fetchApi<Race>(buildContractPath(API_CONTRACT.endpoints.raceById, { id })),
  
  create: (data: Partial<Race>) =>
    fetchApi<Race>(API_CONTRACT.endpoints.racesList, {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  
  update: (id: string, data: Partial<Race>) =>
    fetchApi<Race>(buildContractPath(API_CONTRACT.endpoints.raceById, { id }), {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
  
  delete: (id: string) =>
    fetchApi<void>(buildContractPath(API_CONTRACT.endpoints.raceById, { id }), { method: 'DELETE' }),
  
  clone: (id: string, data: { name: string; raceDate?: string; startTime?: string; copyCategories?: boolean; copyCheckpoints?: boolean; copyRegistrationFields?: boolean }) =>
    fetchApi<Race>(buildContractPath(API_CONTRACT.endpoints.raceClone, { id }), {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  
  archive: (id: string) =>
    fetchApi<Race>(buildContractPath(API_CONTRACT.endpoints.raceArchive, { id }), { method: 'POST' }),
  
  restore: (id: string) =>
    fetchApi<Race>(buildContractPath(API_CONTRACT.endpoints.raceRestore, { id }), { method: 'POST' }),
  
  updateStatus: (id: string, status: Race['status']) =>
    fetchApi<Race>(buildContractPath(API_CONTRACT.endpoints.raceStatus, { id }), {
      method: 'PATCH',
      body: JSON.stringify({ status }),
    }),
}

// Participants API
export const participantsApi = {
  list: (raceId: string, status?: string) =>
    fetchApi<Participant[]>(buildContractPath(
      API_CONTRACT.endpoints.raceParticipants,
      { raceId },
      { status },
    )),
  
  get: (raceId: string, id: string) => fetchApi<Participant>(buildContractPath(
    API_CONTRACT.endpoints.raceParticipantById,
    { raceId, id },
  )),
  
  create: (raceId: string, data: Partial<Participant>) =>
    fetchApi<Participant>(buildContractPath(API_CONTRACT.endpoints.raceParticipants, { raceId }), {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  
  update: (raceId: string, id: string, data: Partial<Participant>) =>
    fetchApi<Participant>(buildContractPath(API_CONTRACT.endpoints.raceParticipantById, { raceId, id }), {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
  
  delete: (raceId: string, id: string) =>
    fetchApi<void>(buildContractPath(API_CONTRACT.endpoints.raceParticipantById, { raceId, id }), { method: 'DELETE' }),
  
  checkIn: (raceId: string, id: string) =>
    fetchApi<Participant>(buildContractPath(API_CONTRACT.endpoints.raceParticipantCheckIn, { raceId, id }), { method: 'POST' }),
  
  listPenalties: (raceId: string, id: string) =>
    fetchApi<Penalty[]>(buildContractPath(API_CONTRACT.endpoints.raceParticipantPenalties, { raceId, id })),
  
  addPenalty: (raceId: string, id: string, data: { seconds: number; reason?: string }) =>
    fetchApi<Penalty>(buildContractPath(API_CONTRACT.endpoints.raceParticipantPenalties, { raceId, id }), {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  
  removePenalty: (raceId: string, id: string, penaltyId: string) =>
    fetchApi<void>(buildContractPath(API_CONTRACT.endpoints.raceParticipantPenaltyDelete, { raceId, id, penaltyId }), { method: 'DELETE' }),
  
  startCheckout: (raceId: string, id: string, promoCode?: string) =>
    fetchApi<Payment>(buildContractPath(
      API_CONTRACT.endpoints.raceParticipantPaymentCheckout,
      { raceId, id },
      { promoCode },
    ), { method: 'POST' }),
  
  refundPayment: (raceId: string, id: string) =>
    fetchApi<Payment>(buildContractPath(API_CONTRACT.endpoints.raceParticipantPaymentRefund, { raceId, id }), { method: 'POST' }),

  stageExternalRefund: (raceId: string, id: string, reason: string) =>
    fetchApi<Payment>(buildContractPath(API_CONTRACT.endpoints.raceParticipantPaymentRefundAttempt, { raceId, id }), {
      method: 'POST', body: JSON.stringify({ reason }),
    }),

  reconcileExternalRefund: (raceId: string, id: string, data: {
    providerReference: string; reason: string; outcome: 'REFUNDED' | 'NOT_REFUNDED'
  }) =>
    fetchApi<Payment>(buildContractPath(API_CONTRACT.endpoints.raceParticipantPaymentRefundReconcile, { raceId, id }), {
      method: 'POST', body: JSON.stringify(data),
    }),
  
  importCsv: (raceId: string, file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return fetchAuthenticated(buildContractPath(API_CONTRACT.endpoints.raceParticipantImport, { raceId }), {
      method: 'POST',
      body: formData,
    }, 'CSV import failed').then(res => res.json())
  },
  
  exportCsv: (raceId: string) =>
    fetchAuthenticated(
      buildContractPath(API_CONTRACT.endpoints.raceParticipantExport, { raceId }),
      undefined,
      'Participant export failed',
    ).then(res => res.blob()),

  listImportJobs: (raceId: string) => fetchApi<ImportJob[]>(buildContractPath(API_CONTRACT.endpoints.raceParticipantImportJobs, { raceId })),
  
  getImportJob: (raceId: string, jobId: string) => fetchApi<ImportJob>(buildContractPath(
    API_CONTRACT.endpoints.raceParticipantImportJobById,
    { raceId, jobId },
  )),

  downloadBib: (raceId: string, participantId: string) =>
    fetchAuthenticated(
      buildContractPath(API_CONTRACT.endpoints.raceBib, { raceId, participantId }),
      undefined,
      'Bib download failed',
    ).then(res => res.blob()),

  downloadBatchBibs: (raceId: string, participantIds: string[]) =>
    fetchAuthenticated(
      buildContractPath(API_CONTRACT.endpoints.raceBibBatch, { raceId }),
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(participantIds),
      },
      'Bib download failed',
    ).then(res => res.blob()),

  review: (raceId: string, id: string, decision: RegistrationDecision, reason?: string) =>
    fetchApi<Participant>(buildContractPath(API_CONTRACT.endpoints.raceParticipantReview, { raceId, id }), {
      method: 'POST',
      body: JSON.stringify({ decision, reason }),
    }),

  verifyPayment: (raceId: string, id: string, data: { status: PaymentStatus; reference?: string; amount?: number }) =>
    fetchApi<Participant>(buildContractPath(API_CONTRACT.endpoints.raceParticipantPayment, { raceId, id }), {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  myRegistrations: () => fetchApi<Registration[]>(API_CONTRACT.endpoints.myRegistrations),
}

// Registration fields / teams API
export const registrationApi = {
  listFields: (raceId: string) => fetchApi<RegistrationField[]>(buildContractPath(API_CONTRACT.endpoints.raceRegistrationFields, { id: raceId })),

  createField: (raceId: string, data: Partial<RegistrationField>) =>
    fetchApi<RegistrationField>(buildContractPath(API_CONTRACT.endpoints.raceRegistrationFields, { id: raceId }), {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  updateField: (raceId: string, id: string, data: Partial<RegistrationField>) =>
    fetchApi<RegistrationField>(buildContractPath(API_CONTRACT.endpoints.raceRegistrationFieldById, { raceId, fieldId: id }), {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  deleteField: (raceId: string, id: string) =>
    fetchApi<void>(buildContractPath(API_CONTRACT.endpoints.raceRegistrationFieldById, { raceId, fieldId: id }), { method: 'DELETE' }),

  listTeams: (raceId: string) => fetchApi<Team[]>(buildContractPath(API_CONTRACT.endpoints.raceTeams, { id: raceId })),

  createTeam: (raceId: string, data: { name: string; clubName?: string }) =>
    fetchApi<Team>(buildContractPath(API_CONTRACT.endpoints.raceTeams, { id: raceId }), {
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
  listFields: (raceId: string) => fetchPublicApi<RegistrationField[]>(buildContractPath(API_CONTRACT.endpoints.publicRaceRegistrationFields, { raceId })),

  register: (raceId: string, payload: PublicRegistrationPayload) =>
    fetchApi<Registration>(buildContractPath(API_CONTRACT.endpoints.raceRegister, { id: raceId }), {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
}

// Checkpoints API
export const checkpointsApi = {
  list: (raceId: string) => fetchApi<Checkpoint[]>(buildContractPath(API_CONTRACT.endpoints.raceCheckpoints, { raceId })),
  
  create: (raceId: string, data: Partial<Checkpoint>) =>
    fetchApi<Checkpoint>(buildContractPath(API_CONTRACT.endpoints.raceCheckpoints, { raceId }), {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  
  update: (raceId: string, id: string, data: Partial<Checkpoint>) =>
    fetchApi<Checkpoint>(buildContractPath(API_CONTRACT.endpoints.raceCheckpointById, { raceId, checkpointId: id }), {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
  
  delete: (raceId: string, id: string) =>
    fetchApi<void>(buildContractPath(API_CONTRACT.endpoints.raceCheckpointById, { raceId, checkpointId: id }), { method: 'DELETE' }),
  
  recordTime: (raceId: string, checkpointId: string, data: { participantId: string; method: string }) =>
    fetchApi<CheckpointRecord>(buildContractPath(API_CONTRACT.endpoints.checkpointRecord, { raceId, checkpointId }), {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  correctRecord: (raceId: string, checkpointId: string, recordId: string, data: { adjustedRecordedAt?: string; reason?: string; verified?: boolean }) =>
    fetchApi<CheckpointRecord>(buildContractPath(API_CONTRACT.endpoints.checkpointRecordCorrect, { raceId, checkpointId, recordId }), {
      method: 'PATCH',
      body: JSON.stringify(data),
    }),

  listRecords: (raceId: string, checkpointId: string) =>
    fetchApi<CheckpointRecord[]>(buildContractPath(API_CONTRACT.endpoints.checkpointRecords, { raceId, checkpointId })),

  exportCheckpoints: (raceId: string, format: 'csv' | 'xlsx') =>
    downloadExport(buildContractPath(API_CONTRACT.endpoints.raceCheckpointExport, { raceId }, { format })),
}

export const rfidApi = {
  listReaders: (raceId: string) => fetchApi<RfidReader[]>(buildContractPath(API_CONTRACT.endpoints.raceRfidReaders, { raceId })),
  getOverview: (raceId: string) => fetchApi<RfidRaceOverview>(buildContractPath(API_CONTRACT.endpoints.raceRfidOverview, { raceId })),
  createReader: (raceId: string, data: { checkpointId: string; name: string; externalId: string }) =>
    fetchApi<RfidReaderCreateResponse>(buildContractPath(API_CONTRACT.endpoints.raceRfidReaders, { raceId }), {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  revokeReader: (raceId: string, readerId: string) =>
    fetchApi<RfidReader>(buildContractPath(API_CONTRACT.endpoints.raceRfidReaderRevoke, { raceId, readerId }), {
      method: 'PATCH',
    }),
}

// Categories API
export const categoriesApi = {
  list: (raceId: string) => fetchApi<RaceCategory[]>(buildContractPath(API_CONTRACT.endpoints.raceCategories, { raceId })),
  
  create: (raceId: string, data: Partial<RaceCategory>) =>
    fetchApi<RaceCategory>(buildContractPath(API_CONTRACT.endpoints.raceCategories, { raceId }), {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  
  update: (raceId: string, id: string, data: Partial<RaceCategory>) =>
    fetchApi<RaceCategory>(buildContractPath(API_CONTRACT.endpoints.raceCategoryById, { raceId, categoryId: id }), {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
  
  delete: (raceId: string, id: string) =>
    fetchApi<void>(buildContractPath(API_CONTRACT.endpoints.raceCategoryById, { raceId, categoryId: id }), { method: 'DELETE' }),
}

// Results API
export const resultsApi = {
  list: (raceId: string) => fetchApi<RaceResult[]>(buildContractPath(API_CONTRACT.endpoints.raceResults, { raceId })),

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

  my: () => fetchApi<RaceResult[]>(API_CONTRACT.endpoints.myResults),
  
  publish: (raceId: string) =>
    fetchApi<void>(buildContractPath(API_CONTRACT.endpoints.raceResultsPublish, { raceId }), { method: 'POST' }),

  teamRankings: (raceId: string) =>
    fetchApi<RankingEntry[]>(buildContractPath(API_CONTRACT.endpoints.raceTeamRankings, { raceId })),

  countryRankings: (raceId: string) =>
    fetchApi<RankingEntry[]>(buildContractPath(API_CONTRACT.endpoints.raceCountryRankings, { raceId })),

  exportResults: (raceId: string, format: 'csv' | 'pdf' | 'xlsx') =>
    downloadExport(buildContractPath(API_CONTRACT.endpoints.raceResultsExport, { raceId }, { format })),

  publicCertificateUrl: (raceId: string, participantId: string, token: string) =>
    `${API_BASE}${buildContractPath(API_CONTRACT.endpoints.publicRaceResultCertificate, { raceId, participantId })}?token=${encodeURIComponent(token)}`,
}

// Tracking API
export const trackingApi = {
  getLiveData: (raceId: string) => fetchApi<LiveTrackingData[]>(buildContractPath(API_CONTRACT.endpoints.raceTrackingLive, { raceId })),
  
  getParticipantTrack: (raceId: string, participantId: string) =>
    fetchApi<{ locations: Array<{ lat: number; lng: number; time: string }> }>(
      buildContractPath(API_CONTRACT.endpoints.raceParticipantTracking, { raceId, participantId })
    ),
}

// Dashboard API
export const dashboardApi = {
  getStats: () => fetchApi<DashboardStats>(API_CONTRACT.endpoints.dashboardStats),
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
  list: () => fetchApi<FavoriteAthlete[]>(API_CONTRACT.endpoints.favorites),

  follow: (raceId: string, participantId: string) =>
    fetchApi<FavoriteAthlete>(API_CONTRACT.endpoints.favorites, {
      method: 'POST',
      body: JSON.stringify({ raceId, participantId }),
    }),

  unfollow: (raceId: string, participantId: string) =>
    fetchApi<void>(buildContractPath(API_CONTRACT.endpoints.favoriteDelete, { participantId }, { raceId }), { method: 'DELETE' }),
}

export const notificationsApi = {
  list: () => fetchApi<AppNotification[]>(API_CONTRACT.endpoints.notifications),

  markRead: (id: string) =>
    fetchApi<AppNotification>(buildContractPath(API_CONTRACT.endpoints.notificationRead, { id }), { method: 'PUT' }),

  registerDevice: (token: string, platform = 'web') =>
    fetchApi<{ status: string }>(API_CONTRACT.endpoints.notificationDevices, {
      method: 'POST',
      body: JSON.stringify({ token, platform }),
    }),

  unregisterDevice: (token: string) =>
    fetchApi<void>(`${API_CONTRACT.endpoints.notificationDevices}?token=${encodeURIComponent(token)}`, {
      method: 'DELETE',
    }),

  preferences: () =>
    fetchApi<NotificationPreference>(API_CONTRACT.endpoints.notificationPreferences),

  updatePreferences: (preferences: NotificationPreference) =>
    fetchApi<NotificationPreference>(API_CONTRACT.endpoints.notificationPreferences, {
      method: 'PUT',
      body: JSON.stringify(preferences),
    }),
}

export const mediaApi = {
  list: (raceId: string) => fetchApi<MediaItem[]>(buildContractPath(API_CONTRACT.endpoints.raceMedia, { raceId })),

  publicList: (raceId: string) => fetchPublicApi<MediaItem[]>(buildContractPath(API_CONTRACT.endpoints.publicRaceMedia, { raceId })),

  upload: async (raceId: string, file: File, caption?: string, participantId?: string) => {
    const formData = new FormData()
    formData.append('file', file)
    if (caption) formData.append('caption', caption)
    if (participantId) formData.append('participantId', participantId)
    const response = await fetchAuthenticated(buildContractPath(API_CONTRACT.endpoints.raceMedia, { raceId }), {
      method: 'POST',
      body: formData,
    }, 'Failed to upload media')
    return response.json() as Promise<MediaItem>
  },

  approve: (raceId: string, id: string) =>
    fetchApi<MediaItem>(buildContractPath(API_CONTRACT.endpoints.raceMediaApprove, { raceId, mediaId: id }), { method: 'POST' }),

  reject: (raceId: string, id: string) =>
    fetchApi<MediaItem>(buildContractPath(API_CONTRACT.endpoints.raceMediaReject, { raceId, mediaId: id }), { method: 'POST' }),

  remove: (raceId: string, id: string) =>
    fetchApi<void>(buildContractPath(API_CONTRACT.endpoints.raceMediaDelete, { raceId, mediaId: id }), { method: 'DELETE' }),
}

export const analyticsApi = {
  summary: (raceId: string) => fetchApi<RaceAnalyticsSummary>(buildContractPath(API_CONTRACT.endpoints.raceAnalyticsSummary, { raceId })),
  demographics: (raceId: string) => fetchApi<Demographics>(buildContractPath(API_CONTRACT.endpoints.raceAnalyticsDemographics, { raceId })),
  registrations: (raceId: string, days = 30) =>
    fetchApi<RegistrationPoint[]>(buildContractPath(API_CONTRACT.endpoints.raceAnalyticsRegistrations, { raceId }, { days })),

  exportAnalytics: (raceId: string, format: 'csv' | 'xlsx') =>
    downloadExport(buildContractPath(API_CONTRACT.endpoints.raceAnalyticsExport, { raceId }, { format })),
}

export const insightsApi = {
  summary: (raceId: string) =>
    fetchApi<RaceInsightsSummary>(buildContractPath(API_CONTRACT.endpoints.raceInsightsSummary, { raceId })),

  anomalies: (raceId: string) =>
    fetchApi<ParticipantAnomaly[]>(buildContractPath(API_CONTRACT.endpoints.raceInsightsAnomalies, { raceId })),
}

export const routeApi = {
  get: (raceId: string) =>
    fetchApi<RoutePoint[]>(buildContractPath(API_CONTRACT.endpoints.raceRoute, { raceId })),

  exportGpx: (raceId: string) =>
    downloadExport(buildContractPath(API_CONTRACT.endpoints.raceRouteGpx, { raceId })),

  importGpx: async (raceId: string, file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    const response = await fetchAuthenticated(buildContractPath(API_CONTRACT.endpoints.raceRouteGpx, { raceId }), {
      method: 'POST',
      body: formData,
    }, 'Failed to import GPX route')
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
  list: (raceId: string) => fetchApi<PromoCode[]>(buildContractPath(API_CONTRACT.endpoints.racePromoCodes, { raceId })),

  create: (raceId: string, data: { code: string; discountType: 'PERCENT' | 'FIXED'; discountValue: number; maxUses?: number; expiresAt?: string }) =>
    fetchApi<PromoCode>(buildContractPath(API_CONTRACT.endpoints.racePromoCodes, { raceId }), { method: 'POST', body: JSON.stringify(data) }),

  setActive: (raceId: string, id: string, active: boolean) =>
    fetchApi<PromoCode>(buildContractPath(API_CONTRACT.endpoints.racePromoCodeActive, { raceId, promoId: id }), {
      method: 'PATCH',
      body: JSON.stringify({ active }),
    }),

  remove: (raceId: string, id: string) =>
    fetchApi<void>(buildContractPath(API_CONTRACT.endpoints.racePromoCodeDelete, { raceId, promoId: id }), { method: 'DELETE' }),
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
    reconnectDelay: 5000,
    heartbeatIncoming: 4000,
    heartbeatOutgoing: 4000,
  })

  stompClient.onConnect = () => {
    stompClient?.subscribe(`/topic/race.${raceId}`, (message) => {
      if (message.body) {
        onMessage(JSON.parse(message.body))
      }
    })
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
