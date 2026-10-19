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
  privacyPolicyAccepted?: boolean;
  privacyPolicyVersion?: string;
  experiences?: RegistrationExperience[];
}

export interface ProfileUpdateRequest {
  firstName: string;
  lastName: string;
  phone?: string;
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
  teamId?: string;
  teamName?: string;
  rejectionReason?: string;
  waitlistPosition?: number;
}

export interface RaceRegistrationRequest {
  categoryId: string;
  emergencyContact: Registration['emergencyContact'];
  waiverAccepted?: boolean;
  shirtSize?: string;
  teamId?: string;
  clubName?: string;
  teamName?: string;
  answers?: { fieldId: string; value?: string }[];
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
  estimatedFinishTime?: string;
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
  recordCheckpoint: (record: CheckpointRecord) => void;
  setBatterySaver: (enabled: boolean) => void;
  clearTrackingData: () => Promise<void>;
  rehydrateFromLocalDb: (raceId: string) => Promise<void>;
  recoverActiveTrackingSession: () => Promise<void>;
}

export interface DeletionStatus {
  deletionRequested: boolean;
  requestedAt?: string | null;
  scheduledFor?: string | null;
}
