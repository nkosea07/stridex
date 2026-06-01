export type UserRole = 'SUPER_ADMIN' | 'RACE_ADMIN' | 'OFFICIAL' | 'VOLUNTEER' | 'ATHLETE' | 'SPECTATOR';

// User and Auth Types
export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  role: UserRole;
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

export interface RegisterRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  phone?: string;
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
  status: 'pending' | 'confirmed' | 'cancelled' | 'dns' | 'dnf' | 'finished' | string;
  registrationDate?: string;
  emergencyContact: {
    name: string;
    phone: string;
    relationship: string;
  };
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
  raceId: string;
  participantId: string;
  bibNumber: string;
  category: string;
  startTime: string;
  finishTime?: string;
  totalTime?: string;
  overallPosition?: number;
  categoryPosition?: number;
  genderPosition?: number;
  splits: CheckpointRecord[];
  status: 'in_progress' | 'finished' | 'dnf' | 'dns' | 'dq';
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
  login: (email: string, password: string) => Promise<void>;
  register: (userData: RegisterRequest) => Promise<AuthResponse>;
  logout: () => void;
  updateProfile: (data: Partial<User>) => Promise<void>;
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
  rehydrateFromLocalDb: (raceId: string) => Promise<void>;
  recoverActiveTrackingSession: () => Promise<void>;
}
