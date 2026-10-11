export type UserRole = 'SUPER_ADMIN' | 'RACE_ADMIN' | 'OFFICIAL' | 'VOLUNTEER' | 'ATHLETE' | 'SPECTATOR'

export interface User {
  id: string
  email: string
  firstName: string
  lastName: string
  role: UserRole
  phone?: string
  isActive: boolean
  emailVerified: boolean
  createdAt: string
}

export type OrganizerApplicationStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface OrganizerApplicationReview {
  id: string
  applicantEmail: string
  applicantFirstName: string
  applicantLastName: string
  emailVerified: boolean
  organizationName: string
  status: OrganizerApplicationStatus
  reviewNotes?: string | null
  createdAt: string
  reviewedAt?: string | null
  reviewedByEmail?: string | null
}

export interface OrganizerApplicationPage {
  items: OrganizerApplicationReview[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type RaceStatus = 'DRAFT' | 'REGISTRATION_OPEN' | 'REGISTRATION_CLOSED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
export type RaceType = 'MARATHON' | 'HALF_MARATHON' | 'ULTRA_MARATHON' | '10K' | '5K' | 'TRAIL' | 'CYCLING' | 'TRIATHLON' | 'OTHER'

export interface Race {
  id: string
  name: string
  description?: string
  raceType: RaceType
  status: RaceStatus
  city?: string
  country?: string
  startLocationLat?: number
  startLocationLng?: number
  finishLocationLat?: number
  finishLocationLng?: number
  raceDate: string
  startTime?: string
  registrationOpens?: string
  registrationCloses?: string
  distanceKm?: number
  maxParticipants?: number
  registrationFee?: number
  currency: string
  bannerImageUrl?: string
  logoUrl?: string
  isPublicRegistration: boolean
  requireMedicalClearance: boolean
  requireApproval: boolean
  waitlistEnabled: boolean
  requireWaiver: boolean
  waiverVersion?: string
  waiverText?: string
  participantCount?: number
  createdAt: string
  archivedAt?: string
}

export type PaymentStatus = 'UNPAID' | 'PENDING' | 'PAID' | 'WAIVED' | 'REFUNDED'
export type RegistrationDecision = 'APPROVE' | 'REJECT' | 'WAITLIST'

export interface Penalty {
  id: string
  participantId: string
  seconds: number
  reason?: string
  createdAt?: string
}

export interface AppNotification {
  id: string
  title: string
  message: string
  type?: string
  read: boolean
  timestamp?: string
}

export interface Payment {
  id: string
  participantId: string
  provider: string
  status: string
  amount?: number
  currency?: string
  reference?: string
  checkoutUrl?: string
}

export interface RegistrationField {
  id: string
  raceId: string
  fieldKey: string
  label: string
  fieldType: 'TEXT' | 'SELECT' | 'CHECKBOX' | 'NUMBER'
  required: boolean
  options: string[]
  displayOrder: number
  active: boolean
  createdAt?: string
}

export interface RegistrationAnswer {
  fieldId: string
  fieldKey?: string
  label?: string
  value?: string
}

export interface Team {
  id: string
  raceId: string
  name: string
  clubName?: string
  status: 'OPEN' | 'LOCKED'
  captainUserId?: string
  memberCount: number
}

export interface Registration {
  id: string
  raceId: string
  userId?: string
  categoryId?: string
  bibNumber?: string
  status: string
  registrationDate?: string
  emergencyContact?: { name?: string; phone?: string; relationship?: string }
  shirtSize?: string
  paymentStatus?: PaymentStatus
  paymentAmount?: number
  teamId?: string
  teamName?: string
  rejectionReason?: string
  waitlistPosition?: number
}

export type ParticipantStatus = 'REGISTERED' | 'CONFIRMED' | 'CHECKED_IN' | 'STARTED' | 'DNF' | 'DNS' | 'DISQUALIFIED' | 'FINISHED' | 'PENDING_REVIEW' | 'WAITLISTED' | 'REJECTED'
export type Gender = 'MALE' | 'FEMALE' | 'OTHER' | 'PREFER_NOT_TO_SAY'
export type ParticipantCredentialType = 'RFID_UHF_TIMING' | 'QR_TICKET' | 'NFC_GATE' | 'BARCODE_GATEPASS'
export type ParticipantCredentialStatus = 'ACTIVE' | 'REVOKED'

export interface ParticipantCredential {
  id: string
  type: ParticipantCredentialType
  value: string
  status: ParticipantCredentialStatus
  assignedAt?: string
  revokedAt?: string
}

export interface Participant {
  id: string
  raceId: string
  categoryId?: string
  firstName: string
  lastName: string
  email: string
  phone?: string
  dateOfBirth?: string
  gender?: Gender
  nationality?: string
  bibNumber?: string
  rfidTag?: string
  status: ParticipantStatus
  emergencyContactName?: string
  emergencyContactPhone?: string
  emergencyContactRelation?: string
  bloodType?: string
  medicalConditions?: string
  allergies?: string
  clubName?: string
  teamName?: string
  registrationSource?: string
  registeredAt: string
  checkedInAt?: string
  isTrackingEnabled: boolean
  shirtSize?: string
  waiverAcceptedAt?: string
  waiverVersion?: string
  approvedAt?: string
  rejectionReason?: string
  waitlistPosition?: number
  paymentStatus?: PaymentStatus
  paymentReference?: string
  paymentAmount?: number
  paymentVerifiedAt?: string
  teamId?: string
  credentials?: ParticipantCredential[]
  lastLocation?: GpsLocation
}

export interface ImportJob {
  id: string
  raceId: string
  fileName: string
  status: string
  totalRows?: number
  processedRows?: number
  successCount?: number
  errorCount?: number
  errorMessage?: string
  createdAt: string
  startedAt?: string
  completedAt?: string
}

export type CheckpointType = 'START' | 'INTERMEDIATE' | 'WATER_STATION' | 'MEDICAL' | 'FINISH'

export interface Checkpoint {
  id: string
  raceId: string
  name: string
  checkpointType: CheckpointType
  distanceFromStartKm?: number
  latitude?: number
  longitude?: number
  orderIndex: number
  cutoffTime?: string
  hasRfidReader: boolean
  hasQrScanner: boolean
  recordCount?: number
}

export type RecordMethod = 'RFID' | 'QR_CODE' | 'MANUAL' | 'GPS_AUTO'

export interface CheckpointRecord {
  id: string
  participantId: string
  checkpointId: string
  raceId: string
  recordedAt: string
  recordMethod: RecordMethod
  recordedById?: string
  splitTime?: string
  elapsedTime?: string
  isVerified: boolean
  notes?: string
  participant?: Participant
}

export type RfidGatewayStatus = 'ONLINE' | 'DEGRADED' | 'OFFLINE'
export type RfidReaderStatus = 'ACTIVE' | 'REVOKED'

export interface RfidReader {
  id: string
  raceId: string
  checkpointId?: string
  name: string
  externalId: string
  status: RfidReaderStatus
  lastSeenAt?: string
  lastIp?: string
  gatewayName?: string
  gatewayStatus?: RfidGatewayStatus
  gatewayBacklog?: number
  firmwareVersion?: string
  hardwareModel?: string
  hardwareProfile?: string
  readerClass?: string
  lastError?: string
  lastHeartbeatAt?: string
  lastProcessedAt?: string
  createdAt?: string
  updatedAt?: string
}

export interface RfidRaceOverview {
  totalReaders: number
  activeReaders: number
  onlineReaders: number
  degradedReaders: number
  offlineReaders: number
  totalEvents: number
  processedEvents: number
  queueDepth: number
  duplicateCount: number
  duplicateRate: number
  unknownCredentialCount: number
  unknownCredentialRate: number
  invalidReaderCount: number
  errorCount: number
  averageIngressLagMillis?: number
  oldestQueuedReadAgeSeconds?: number
  readerSilenceTimeoutSeconds: number
  gatewayBacklogDegradedThreshold: number
  readers?: RfidReaderBreakdown[]
  eventTrend?: RfidEventTrendPoint[]
}

export interface RfidReaderBreakdown {
  readerId: string
  name?: string
  checkpointId?: string
  status?: string
  gatewayStatus?: string
  backlog?: number
  lastHeartbeatAgeSeconds?: number
  firmwareVersion?: string
  hardwareModel?: string
  hardwareProfile?: string
  readerClass?: string
  lastError?: string
}

export interface RfidEventTrendPoint {
  bucket?: string
  total: number
  accepted: number
  duplicate: number
  unknownCredential: number
  error: number
}

export interface GpsLocation {
  id: string
  participantId: string
  raceId: string
  latitude: number
  longitude: number
  altitude?: number
  accuracy?: number
  speed?: number
  heading?: number
  batteryLevel?: number
  recordedAt: string
}

export interface RaceCategory {
  id: string
  raceId: string
  name: string
  description?: string
  minAge?: number
  maxAge?: number
  gender?: string
  bibPrefix?: string
  maxParticipants?: number
  registrationFee?: number
  participantCount?: number
}

export interface RaceResult {
  id: string
  participantId: string
  raceId: string
  categoryId?: string
  status?: string
  gunTime?: string
  chipTime?: string
  overallPosition?: number
  genderPosition?: number
  categoryPosition?: number
  ageGroup?: string
  ageGroupPosition?: number
  medal?: string | null
  averagePace?: string
  averageSpeed?: number
  isOfficial: boolean
  isDisqualified: boolean
  disqualificationReason?: string
  penaltySeconds?: number
  participant?: Participant
}

export interface RaceResultPage {
  items: RaceResult[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  revision: number
}

export interface RankingEntry {
  name: string
  finishers: number
  totalSeconds: number
  totalTime?: string
  averageTime?: string
  bestTime?: string
}

export interface MediaItem {
  id: string
  raceId: string
  participantId?: string | null
  mediaType: string
  url: string
  thumbnailUrl?: string | null
  width?: number | null
  height?: number | null
  caption?: string | null
  status: string
  createdAt?: string
}

export interface RaceAnalyticsSummary {
  totalRegistrations: number
  statusCounts: Record<string, number>
  paidCount: number
  unpaidCount: number
  refundedCount: number
  revenue: number
  completionRate: number
}

export interface Demographics {
  gender: Record<string, number>
  ageGroups: Record<string, number>
  countries: Record<string, number>
  shirtSizes: Record<string, number>
}

export interface RegistrationPoint {
  date: string
  count: number
}

export interface PromoCode {
  id: string
  raceId?: string | null
  code: string
  discountType: 'PERCENT' | 'FIXED'
  discountValue: number
  maxUses?: number | null
  usedCount: number
  active: boolean
  expiresAt?: string | null
}

export interface DashboardStats {
  totalRaces: number
  activeRaces: number
  totalParticipants: number
  liveTracking: number
  upcomingRaces: number
  completedToday: number
}

export interface LiveTrackingData {
  participantId: string
  bibNumber: string
  participantName: string
  latitude: number
  longitude: number
  speed: number
  lastCheckpoint?: string
  elapsedTime?: string
  status: ParticipantStatus
  predictedFinishTime?: string
  distanceRemaining?: number
  averagePace?: string
}

export interface PublicAthleteSummary {
  participantId: string
  participantName: string
  bibNumber?: string
  status: ParticipantStatus | string
  lastCheckpoint?: string
  elapsedTime?: string
  averagePace?: string
  predictedFinishTime?: string
  distanceRemaining?: number
}

export interface PublicRaceHub {
  raceId: string
  raceName: string
  status: RaceStatus | string
  raceDate?: string
  city?: string
  country?: string
  distanceKm?: number
  bannerImageUrl?: string
  logoUrl?: string
  resultsPublished?: boolean
  registrationOpens?: string | null
  registrationCloses?: string | null
  maxParticipants?: number | null
  registrationFee?: number | null
  currency?: string | null
  isPublicRegistration?: boolean | null
  requireApproval?: boolean | null
  waitlistEnabled?: boolean | null
  requireWaiver?: boolean | null
  waiverVersion?: string | null
  waiverText?: string | null
  participantCount?: number | null
  categories?: RaceCategory[]
  leaderboard: PublicAthleteSummary[]
}

export interface PublicRaceResult {
  participantId: string
  participantName?: string | null
  bibNumber?: string | null
  status?: string | null
  totalTime?: string | null
  finishTime?: string | null
  category?: string | null
  overallPosition?: number | null
  genderPosition?: number | null
  categoryPosition?: number | null
  certificateToken?: string | null
}

export interface PublicRaceResultPage {
  items: PublicRaceResult[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  revision: number
}

export interface PublicRaceLiveFeed {
  items: PublicAthleteSummary[]
  nextCursor?: string | null
  refreshedAt: string
}

export interface PublicCheckpointSplit {
  name?: string
  time?: string
  distanceMeters?: number
  durationSeconds?: number
  paceSecondsPerKm?: number
}

export interface TrackPoint {
  lat: number
  lng: number
  time?: string
  speed?: number
}

export interface PublicParticipantTrack {
  points: TrackPoint[]
}

export interface HeatCell {
  latitude: number
  longitude: number
  weight: number
}

export interface RoutePoint {
  sequence: number
  latitude: number
  longitude: number
  elevation?: number | null
}

export interface EmergencyAlert {
  id: string
  raceId: string
  participantId?: string | null
  participantName?: string | null
  latitude?: number | null
  longitude?: number | null
  message?: string | null
  status: string
  createdAt?: string
}

export interface FavoriteAthlete {
  raceId?: string
  raceName?: string
  participantId: string
  participantName?: string
  bibNumber?: string
  status?: string
  lastCheckpoint?: string
  distanceRemaining?: number
}

export interface PublicAthleteTracking {
  participantId: string
  participantName: string
  bibNumber?: string
  status: ParticipantStatus | string
  lastCheckpoint?: string
  elapsedTime?: string
  averagePace?: string
  predictedFinishTime?: string
  distanceRemaining?: number
  splits: PublicCheckpointSplit[]
}

export interface DeletionStatus {
  deletionRequested: boolean
  requestedAt?: string | null
  scheduledFor?: string | null
}
