export type UserRole = 'SUPER_ADMIN' | 'RACE_ADMIN' | 'OFFICIAL' | 'VOLUNTEER' | 'ATHLETE' | 'SPECTATOR';
export type UserCapability = 'ATHLETE' | 'SPECTATOR';
export type RegistrationExperience = UserCapability;

// User and Auth Types
export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  role: UserRole;
  capabilities: UserCapability[];
  preferredCapability: UserCapability | null;
  phone?: string;
  isActive?: boolean;
  emailVerified?: boolean;
  createdAt?: string;
  profileImage?: string;
  privacyPolicyVersion?: string | null;
  termsVersion?: string | null;
  legalAcceptanceRequired?: boolean | null;
  preferredLocale?: string | null;
}

export interface AuthResponse {
  token?: string | null;
  refreshToken?: string | null;
  requiresEmailVerification?: boolean;
  user: User;
}

export type MobileLoginResult = { kind: 'authenticated'; user: User };

export interface RegisterRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  phone?: string;
  privacyPolicyAccepted: boolean;
  privacyPolicyVersion: string;
  termsAccepted: boolean;
  termsVersion: string;
  experiences?: RegistrationExperience[];
}

export interface ProfileUpdateRequest {
  firstName?: string;
  lastName?: string;
  phone?: string;
  preferredLocale?: string;
}

export interface CapabilityUpdateRequest {
  capabilities: UserCapability[];
  preferredCapability: UserCapability;
}

export interface FavoriteAthlete {
  raceId: string;
  raceName?: string;
  participantId: string;
  participantName?: string;
  bibNumber?: string;
  status?: string;
  lastCheckpoint?: string;
  distanceRemaining?: number;
}

export interface PublicRaceResult {
  participantId: string;
  participantName?: string;
  bibNumber?: string;
  status?: string;
  totalTime?: string;
  category?: string;
  overallPosition?: number;
}

export interface PublicAthleteSummary {
  participantId: string;
  participantName?: string | null;
  bibNumber?: string | null;
  status?: string | null;
  lastCheckpoint?: string | null;
  elapsedTime?: string | null;
  averagePace?: string | null;
  predictedFinishTime?: string | null;
  distanceRemaining?: number | null;
  rank?: number | null;
}

export interface HeatCell {
  latitude: number;
  longitude: number;
  weight: number;
}

export type PublicRaceMediaType = 'IMAGE' | 'VIDEO';

export interface PublicRaceMedia {
  id: string;
  mediaType: PublicRaceMediaType;
  caption: string | null;
  hasThumbnail: boolean;
}

export interface PublicRaceResultPage {
  items: PublicRaceResult[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  revision: number;
}

// Race Types
export interface Race {
  id: string;
  name: string;
  description?: string;
  date?: string;
  raceDate?: string;
  startTime?: string;
  city?: string;
  country?: string;
  distanceKm: number;
  location?: string;
  distance?: number; // mapped app-level field (km)
  status: 'upcoming' | 'registration_open' | 'registration_closed' | 'in_progress' | 'completed' | 'cancelled';
  categories: RaceCategory[];
  checkpoints: Checkpoint[];
  routeCoordinates?: Coordinate[];
  bannerImage?: string;
  organizer: string;
  registrationDeadline?: string;
  maxParticipants?: number;
  currentParticipants?: number;
  currency?: string;
  isPublicRegistration?: boolean;
  requireApproval?: boolean;
  waitlistEnabled?: boolean;
  requireWaiver?: boolean;
  waiverVersion?: string;
  waiverText?: string;
  participantCount?: number;
}

export interface RegistrationField {
  id: string;
  raceId: string;
  fieldKey: string;
  label: string;
  fieldType: 'TEXT' | 'SELECT' | 'CHECKBOX' | 'NUMBER';
  required: boolean;
  options: string[];
  displayOrder: number;
  active: boolean;
}

export interface RaceCategory {
  id: string;
  raceId?: string;
  name: string;
  description?: string;
  minAge?: number;
  maxAge?: number;
  bibPrefix?: string;
  participantCount?: number;
  distance?: number;
  distanceKm?: number;
  ageMin?: number;
  ageMax?: number;
  gender?: 'male' | 'female' | 'any';
  maxParticipants?: number;
  registrationFee?: number;
}

export interface Checkpoint {
  id: string;
  raceId?: string;
  name: string;
  checkpointType?: string;
  distanceFromStart: number;
  distanceFromStartKm?: number;
  latitude?: number;
  longitude?: number;
  orderIndex?: number;
  location: Coordinate;
  cutoffTime?: string;
  hasRfidReader?: boolean;
  hasQrScanner?: boolean;
  recordCount?: number;
  amenities?: string[];
  type: 'start' | 'checkpoint' | 'finish' | 'water_station' | 'medical';
}

export interface BackendCheckpointDto {
  id: string;
  raceId: string;
  name: string;
  checkpointType: string;
  distanceFromStartKm?: number;
  latitude?: number;
  longitude?: number;
  orderIndex?: number;
  cutoffTime?: string;
  hasRfidReader?: boolean;
  hasQrScanner?: boolean;
  recordCount?: number;
}

export interface Coordinate {
  latitude: number;
  longitude: number;
}

// Participant/Registration Types
export interface Registration {
  id: string;
  raceId: string;
  userId: string;
  categoryId: string;
  bibNumber?: string;
  status: 'pending' | 'pending_review' | 'waitlisted' | 'rejected' | 'confirmed' | 'cancelled' | 'dns' | 'dnf' | 'finished' | string;
  registrationDate?: string;
  emergencyContact: {
    name: string;
    phone: string;
    relationship: string;
  };
  shirtSize?: string;
  paymentStatus?: 'UNPAID' | 'PENDING' | 'PAID' | 'WAIVED' | 'REFUNDED';
  paymentAmount?: number | null;
  amountDue?: number | null;
  currency?: string | null;
  mediaPublicationConsent?: boolean;
  publicLocationSharing?: boolean;
  canWithdraw?: boolean;
  teamId?: string;
  teamName?: string;
  rejectionReason?: string;
  waitlistPosition?: number;
}

export type RegistrationGender = 'MALE' | 'FEMALE' | 'OTHER' | 'PREFER_NOT_TO_SAY';

export interface RaceRegistrationRequest {
  categoryId: string;
  gender: RegistrationGender;
  dateOfBirth: string;
  emergencyContact: Registration['emergencyContact'];
  waiverAccepted?: boolean;
  shirtSize?: string;
  teamId?: string;
  clubName?: string;
  teamName?: string;
  answers?: { fieldId: string; value?: string }[];
  mediaPublicationConsent?: boolean;
  bloodType?: string;
  medicalConditions?: string;
  allergies?: string;
}

export type ParticipantCredentialType = 'RFID_UHF_TIMING' | 'QR_TICKET' | 'NFC_GATE' | 'BARCODE_GATEPASS';
export type ParticipantCredentialStatus = 'ACTIVE' | 'REVOKED';

export interface ParticipantCredential {
  id: string;
  type: ParticipantCredentialType;
  value: string;
  status: ParticipantCredentialStatus;
  assignedAt?: string;
  revokedAt?: string;
}

// Tracking Types
export interface GpsLocation {
  latitude: number;
  longitude: number;
  altitude?: number;
  accuracy?: number;
  speed?: number;
  heading?: number;
  timestamp: string;
}

export interface CheckpointRecord {
  id: string;
  checkpointId: string;
  timestamp: string;
  method: 'gps' | 'rfid' | 'qr_code' | 'manual';
  pace?: number;
  splitTime?: string;
}

export interface LiveStats {
  currentPace: number; // minutes per km
  averagePace: number;
  distanceCovered: number;
  elapsedTime: number; // in seconds
  currentPosition?: number;
  heartRate?: number;
}

// Results Types
export interface RaceResult {
  id?: string;
  raceId: string;
  participantId: string;
  bibNumber: string;
  category: string;
  startTime: string;
  finishTime?: string;
  totalTime?: string;
  chipTime?: string;
  gunTime?: string;
  averagePace?: string;
  overallPosition?: number;
  categoryPosition?: number;
  genderPosition?: number;
  splits: CheckpointRecord[];
  status: 'in_progress' | 'finished' | 'dnf' | 'dns' | 'dq';
  raceName?: string;
  official?: boolean;
  certificateAvailable?: boolean;
  certificateToken?: string | null;
}

export interface AthleteHistoryEntry {
  raceId: string;
  raceName?: string | null;
  raceDate?: string | null;
  distanceKm?: number | null;
  chipTime?: string | null;
  overallPosition?: number | null;
  official?: boolean;
  disqualified?: boolean;
  medal?: string | null;
}

export interface PublicCheckpointSplit {
  name: string;
  time?: string | null;
  distanceMeters?: number | null;
  durationSeconds?: number | null;
  paceSecondsPerKm?: number | null;
}

export interface PublicAthleteTracking {
  participantId: string;
  participantName?: string | null;
  bibNumber?: string | null;
  status?: string | null;
  lastCheckpoint?: string | null;
  elapsedTime?: string | null;
  averagePace?: string | null;
  predictedFinishTime?: string | null;
  distanceRemaining?: number | null;
  splits: PublicCheckpointSplit[];
}

export interface StaffParticipant {
  id: string;
  bibNumber?: string | null;
  firstName?: string | null;
  lastName?: string | null;
  categoryName?: string | null;
  waveName?: string | null;
  status?: string | null;
  checkedInAt?: string | null;
}

export interface StationRecord {
  id: string;
  participantId: string;
  participantName?: string | null;
  bibNumber?: string | null;
  recordedAt: string;
  recordMethod?: string | null;
  recordedById?: string | null;
  passNumber?: number | null;
}

export interface RaceWave {
  id: string;
  name: string;
  scheduledStart?: string | null;
  startedAt?: string | null;
  participants?: number;
}

export interface NotificationPreferences {
  emailEnabled: boolean;
  pushEnabled: boolean;
  smsEnabled: boolean;
  whatsappEnabled: boolean;
}

export interface ConsentState {
  dataProcessingConsentAt?: string | null;
  marketingConsent: boolean;
}

export interface PersonalBest {
  distanceKm: number;
  chipTime?: string | null;
  raceId?: string | null;
  raceName?: string | null;
  resultCount?: number;
}

export interface ResultQuery {
  id: string;
  raceId: string;
  participantId: string;
  checkpointId?: string | null;
  checkpointName?: string | null;
  message: string;
  status: 'OPEN' | 'RESOLVED' | 'REJECTED' | string;
  resolutionNote?: string | null;
  createdAt?: string | null;
  resolvedAt?: string | null;
}

export interface PaymentCheckout {
  id?: string;
  status?: string;
  amount?: number;
  payableAmount?: number;
  currency?: string;
  checkoutUrl?: string | null;
}

export interface RaceResultPage {
  items: RaceResult[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  revision: number;
}

// Notification Types
export interface Notification {
  id: string;
  title: string;
  message: string;
  type: 'race_update' | 'checkpoint_passed' | 'achievement' | 'reminder' | 'alert';
  timestamp: string;
  read: boolean;
  data?: Record<string, unknown>;
}

// Store Types
export interface AuthState {
  user: User | null;
  token: string | null;
  refreshToken: string | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  login: (email: string, password: string) => Promise<MobileLoginResult>;
  register: (userData: RegisterRequest) => Promise<AuthResponse>;
  logout: () => Promise<void>;
  updateProfile: (data: ProfileUpdateRequest) => Promise<void>;
  updateCapabilities: (data: CapabilityUpdateRequest) => Promise<void>;
  acceptLegalDocuments: () => Promise<void>;
}

export type ThemePreference = 'light' | 'dark' | 'system';

export interface TrackingState {
  isTracking: boolean;
  currentRace: Race | null;
  locations: GpsLocation[];
  checkpointRecords: CheckpointRecord[];
  liveStats: LiveStats | null;
  batterySaver: boolean;
  startTracking: (race: Race) => Promise<void>;
  stopTracking: () => void;
  addLocation: (location: GpsLocation) => void;
  recordCheckpoint: (
    record: CheckpointRecord,
    scanToken: string,
    location?: GpsLocation,
  ) => Promise<{ status: 'VERIFIED' | 'PROVISIONAL'; reason: string | null }>;
  setBatterySaver: (enabled: boolean) => void;
  clearTrackingData: () => Promise<void>;
  rehydrateFromLocalDb: (raceId: string) => Promise<void>;
  recoverActiveTrackingSession: () => Promise<void>;
  trackingStopNotice: { raceId: string; reason: 'not_registered' | 'not_permitted' | 'closed' } | null;
  acknowledgeTrackingStop: () => Promise<void>;
}

export interface DeletionStatus {
  deletionRequested: boolean;
  requestedAt?: string | null;
  scheduledFor?: string | null;
  blockers?: string[];
}
