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
  participantCount?: number
  createdAt: string
}

export type ParticipantStatus = 'REGISTERED' | 'CONFIRMED' | 'CHECKED_IN' | 'STARTED' | 'DNF' | 'DNS' | 'DISQUALIFIED' | 'FINISHED'
export type Gender = 'MALE' | 'FEMALE' | 'OTHER' | 'PREFER_NOT_TO_SAY'

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
  lastLocation?: GpsLocation
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
  averagePace?: string
  averageSpeed?: number
  isOfficial: boolean
  isDisqualified: boolean
  disqualificationReason?: string
  participant?: Participant
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
  leaderboard: PublicAthleteSummary[]
}

export interface PublicCheckpointSplit {
  name?: string
  time?: string
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
