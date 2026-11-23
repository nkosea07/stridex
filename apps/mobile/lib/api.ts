import AsyncStorage from '@react-native-async-storage/async-storage';
import * as SecureStore from 'expo-secure-store';
import type {
  AuthResponse,
  CapabilityUpdateRequest,
  FavoriteAthlete,
  PublicRaceResultPage,
  RegisterRequest,
  User,
  ProfileUpdateRequest,
  Race,
  Registration,
  RegistrationField,
  RaceRegistrationRequest,
  GpsLocation,
  RaceResult,
  RaceResultPage,
  ResultQuery,
  PublicAthleteSummary,
  PublicRaceMedia,
  HeatCell,
  NotificationPreferences,
  ConsentState,
  PersonalBest,
  StaffParticipant,
  StationRecord,
  RaceWave,
  PublicAthleteTracking,
  AthleteHistoryEntry,
  PaymentCheckout,
  Notification,
  BackendCheckpointDto,
  DeletionStatus,
  UserCapability,
  UserRole,
} from '@/types';
import { normalizeUser } from './capabilities';
import { isValidCoordinate, toCoordinates, type Coordinate, type TrackPoint } from './routeGeometry';
import { getInstallId } from './installId';
import { API_CONTRACT, getVersionedApiBase } from '@race-tracker/contracts';

const API_ROOT = process.env.EXPO_PUBLIC_API_URL || API_CONTRACT.mobile.defaultApiRoot;
const API_BASE_URL = getVersionedApiBase(API_ROOT);

type PageResponse<T> = {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type RacePage = PageResponse<Race>;
export type AssignedCheckpoint = { raceId: string; raceName: string; checkpointId: string; checkpointName: string };
export type CheckpointEmergencyReceipt = { id: string; deliveryStatus: 'QUEUED' | 'DISPATCHED' | 'ESCALATION_REQUIRED' | 'LEGACY_UNVERIFIED' };

export function parseAssignedCheckpointPage(value: unknown): PageResponse<AssignedCheckpoint> {
  const page = parseRequiredRecord(value, 'Assigned checkpoints response was malformed.');
  if (!Array.isArray(page.content)) throw new Error('Assigned checkpoints response was malformed.');
  const number = parseFiniteInteger(page.number, 'Assigned checkpoint page number was malformed.');
  const size = parseFiniteInteger(page.size, 'Assigned checkpoint page size was malformed.', 1);
  const totalElements = parseFiniteInteger(page.totalElements, 'Assigned checkpoint total was malformed.');
  const totalPages = parseFiniteInteger(page.totalPages, 'Assigned checkpoint page count was malformed.');
  if (![number, size, totalElements, totalPages].every(Number.isSafeInteger)) {
    throw new Error('Assigned checkpoint page metadata was malformed.');
  }
  const expectedPages = Math.ceil(totalElements / size);
  const expectedContentLength = Math.min(size, Math.max(0, totalElements - number * size));
  if (totalPages !== expectedPages || (totalPages === 0 ? number !== 0 : number >= totalPages)
    || page.content.length !== expectedContentLength) {
    throw new Error('Assigned checkpoint page metadata was inconsistent.');
  }
  return {
    content: page.content.map((item: unknown) => {
      const row = parseRequiredRecord(item, 'Assigned checkpoint was malformed.');
      return {
        raceId: parseRequiredString(row.raceId, 'Assigned checkpoint race id was missing.'),
        raceName: parseRequiredString(row.raceName, 'Assigned checkpoint race name was missing.'),
        checkpointId: parseRequiredString(row.checkpointId, 'Assigned checkpoint id was missing.'),
        checkpointName: parseRequiredString(row.checkpointName, 'Assigned checkpoint name was missing.'),
      };
    }),
    number,
    size,
    totalElements,
    totalPages,
  };
}

export function parseCheckpointEmergencyReceipt(value: unknown): CheckpointEmergencyReceipt {
  const receipt = parseRequiredRecord(value, 'Incident receipt was malformed.');
  const deliveryStatus = receipt.deliveryStatus;
  if (deliveryStatus !== 'QUEUED' && deliveryStatus !== 'DISPATCHED'
    && deliveryStatus !== 'ESCALATION_REQUIRED' && deliveryStatus !== 'LEGACY_UNVERIFIED') {
    throw new Error('Incident receipt delivery status was malformed.');
  }
  return { id: parseRequiredString(receipt.id, 'Incident receipt id was missing.'), deliveryStatus };
}

type SessionInvalidatedHandler = () => void | Promise<void>;

let sessionInvalidatedHandler: SessionInvalidatedHandler | null = null;

export function setSessionInvalidatedHandler(handler: SessionInvalidatedHandler | null): void {
  sessionInvalidatedHandler = handler;
}

export const APP_UPDATE_REQUIRED = 'APP_UPDATE_REQUIRED';

type UpdateRequiredHandler = () => void;

let updateRequiredHandler: UpdateRequiredHandler | null = null;
let appIdentity: { version: string | null; platform: string | null } = { version: null, platform: null };

export function setUpdateRequiredHandler(handler: UpdateRequiredHandler | null): void {
  updateRequiredHandler = handler;
}

export function setAppIdentity(identity: { version: string | null; platform: string | null }): void {
  appIdentity = identity;
}

export type AppConfig = {
  minimumVersion: string | null;
  latestVersion: string | null;
  iosStoreUrl: string | null;
  androidStoreUrl: string | null;
};

function optionalString(value: unknown): string | null {
  return typeof value === 'string' && value.trim() ? value.trim() : null;
}

export function parseAppConfig(raw: unknown): AppConfig {
  const value = raw && typeof raw === 'object' ? raw as Record<string, unknown> : {};
  return {
    minimumVersion: optionalString(value.minimumVersion),
    latestVersion: optionalString(value.latestVersion),
    iosStoreUrl: optionalString(value.iosStoreUrl),
    androidStoreUrl: optionalString(value.androidStoreUrl),
  };
}

export type ApiErrorKind = 'network' | 'not_found' | 'unknown';

export class ApiRequestError extends Error {
  constructor(
    message: string,
    public readonly status?: number,
    public readonly isNetworkError = false,
    public readonly code?: string,
  ) {
    super(message);
    this.name = 'ApiRequestError';
  }
}

export function getApiErrorKind(error: unknown): ApiErrorKind {
  if (error instanceof ApiRequestError && error.isNetworkError) return 'network';
  if (error instanceof ApiRequestError && error.status === 404) return 'not_found';
  return 'unknown';
}

export function isApiConflict(error: unknown): boolean {
  return error instanceof ApiRequestError && error.status === 409;
}

export function getNonEmptyRouteId(value: unknown): string | null {
  const candidate = Array.isArray(value) ? value[0] : value;
  if (typeof candidate !== 'string') return null;
  const normalized = candidate.trim();
  if (!normalized || normalized === 'undefined' || normalized === 'null') return null;
  return normalized;
}

function requireNonEmptyId(value: string, field: string): string {
  const normalized = getNonEmptyRouteId(value);
  if (!normalized) throw new Error(`${field} is required.`);
  return normalized;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function parseRequiredRecord(value: unknown, message: string): Record<string, unknown> {
  if (!isRecord(value)) throw new Error(message);
  return value;
}

function parseFiniteInteger(value: unknown, message: string, minimum = 0): number {
  if (typeof value !== 'number' || !Number.isInteger(value) || value < minimum) {
    throw new Error(message);
  }
  return value;
}

function parseRequiredString(value: unknown, message: string): string {
  if (typeof value !== 'string' || !value.trim()) throw new Error(message);
  return value.trim();
}

function parseOptionalString(value: unknown, message: string): string | undefined {
  if (value === undefined || value === null) return undefined;
  return parseRequiredString(value, message);
}

function parseOptionalBoolean(value: unknown, message: string): boolean | undefined {
  if (value === undefined || value === null) return undefined;
  if (typeof value !== 'boolean') throw new Error(message);
  return value;
}

function isUserRole(value: unknown): value is UserRole {
  return value === 'SUPER_ADMIN'
    || value === 'RACE_ADMIN'
    || value === 'OFFICIAL'
    || value === 'VOLUNTEER'
    || value === 'ATHLETE'
    || value === 'SPECTATOR';
}

function isUserCapability(value: unknown): value is UserCapability {
  return value === 'ATHLETE' || value === 'SPECTATOR';
}

function parseUser(value: unknown): User {
  const user = parseRequiredRecord(value, 'User response was malformed.');
  const role = user.role;
  if (!isUserRole(role)) throw new Error('User response was missing a valid role.');

  const declaredCapabilities = user.capabilities === undefined || user.capabilities === null
    ? []
    : user.capabilities;
  if (!Array.isArray(declaredCapabilities) || declaredCapabilities.some((item) => !isUserCapability(item))) {
    throw new Error('User response contained invalid capabilities.');
  }

  const preferredCapability = user.preferredCapability === undefined || user.preferredCapability === null
    ? null
    : user.preferredCapability;
  if (preferredCapability !== null && !isUserCapability(preferredCapability)) {
    throw new Error('User response contained an invalid preferred capability.');
  }

  return normalizeUser({
    id: parseRequiredString(user.id, 'User response was missing an id.'),
    email: parseRequiredString(user.email, 'User response was missing an email.'),
    firstName: parseRequiredString(user.firstName, 'User response was missing a first name.'),
    lastName: parseRequiredString(user.lastName, 'User response was missing a last name.'),
    role,
    capabilities: declaredCapabilities,
    preferredCapability,
    phone: parseOptionalString(user.phone, 'User response contained an invalid phone number.'),
    isActive: parseOptionalBoolean(user.isActive, 'User response contained an invalid active state.'),
    emailVerified: parseOptionalBoolean(user.emailVerified, 'User response contained an invalid email verification state.'),
    createdAt: parseOptionalString(user.createdAt, 'User response contained an invalid createdAt value.'),
    profileImage: parseOptionalString(user.profileImage, 'User response contained an invalid profile image.'),
    privacyPolicyVersion: parseOptionalString(user.privacyPolicyVersion, 'User response contained an invalid privacy policy version.') ?? null,
    termsVersion: parseOptionalString(user.termsVersion, 'User response contained an invalid terms version.') ?? null,
    legalAcceptanceRequired: parseOptionalBoolean(user.legalAcceptanceRequired, 'User response contained an invalid legal acceptance state.') ?? null,
    preferredLocale: parseOptionalString(user.preferredLocale, 'User response contained an invalid preferred locale.') ?? null,
  });
}

function parseAuthResponse(value: unknown): AuthResponse {
  const response = parseRequiredRecord(value, 'Authentication response was malformed.');
  const token = response.token === undefined || response.token === null
    ? null
    : parseRequiredString(response.token, 'Authentication response contained an invalid access token.');
  const refreshToken = response.refreshToken === undefined || response.refreshToken === null
    ? null
    : parseRequiredString(response.refreshToken, 'Authentication response contained an invalid refresh token.');
  const requiresEmailVerification = response.requiresEmailVerification === undefined
    ? false
    : response.requiresEmailVerification;
  if (typeof requiresEmailVerification !== 'boolean') {
    throw new Error('Authentication response contained an invalid verification state.');
  }

  return { token, refreshToken, requiresEmailVerification, user: parseUser(response.user) };
}

export type SelfScanOutcome = { status: 'VERIFIED' | 'PROVISIONAL'; reason: string | null };

function parseSelfScanOutcome(value: unknown): SelfScanOutcome {
  const record = value && typeof value === 'object' ? (value as Record<string, unknown>) : {};
  const status = record.status === 'VERIFIED' ? 'VERIFIED' : 'PROVISIONAL';
  return { status, reason: typeof record.reason === 'string' ? record.reason : null };
}

function parseDeletionStatus(value: unknown): DeletionStatus {
  const status = parseRequiredRecord(value, 'Account deletion response was malformed.');
  if (typeof status.deletionRequested !== 'boolean') {
    throw new Error('Account deletion response was missing its request state.');
  }
  return {
    deletionRequested: status.deletionRequested,
    requestedAt: status.requestedAt === null ? null : parseOptionalString(status.requestedAt, 'Account deletion response contained an invalid request date.'),
    scheduledFor: status.scheduledFor === null ? null : parseOptionalString(status.scheduledFor, 'Account deletion response contained an invalid schedule date.'),
    blockers: Array.isArray(status.blockers)
      ? status.blockers.filter((code): code is string => typeof code === 'string')
      : [],
  };
}

function parseNotificationPreferences(value: unknown): NotificationPreferences {
  const raw = parseRequiredRecord(value, 'Notification preferences response was malformed.');
  return {
    emailEnabled: raw.emailEnabled !== false,
    pushEnabled: raw.pushEnabled !== false,
    smsEnabled: raw.smsEnabled !== false,
    whatsappEnabled: raw.whatsappEnabled !== false,
  };
}

function parseNotification(value: unknown): Notification {
  const notification = parseRequiredRecord(value, 'Notifications response was malformed.');
  const type = notification.type;
  if (type !== 'race_update' && type !== 'checkpoint_passed' && type !== 'achievement'
    && type !== 'reminder' && type !== 'alert') {
    throw new Error('Notifications response contained an invalid notification type.');
  }
  if (typeof notification.read !== 'boolean') throw new Error('Notifications response contained an invalid read state.');

  return {
    id: parseRequiredString(notification.id, 'Notifications response was missing an id.'),
    title: parseRequiredString(notification.title, 'Notifications response was missing a title.'),
    message: parseRequiredString(notification.message, 'Notifications response was missing a message.'),
    type,
    timestamp: parseRequiredString(notification.timestamp, 'Notifications response was missing a timestamp.'),
    read: notification.read,
    data: isRecord(notification.data) ? notification.data : undefined,
  };
}

function parseRace(value: unknown): Race {
  const race = parseRequiredRecord(value, 'Race response was malformed.');
  if (typeof race.id !== 'string' || !race.id.trim() || typeof race.name !== 'string' || !race.name.trim()) {
    throw new Error('Race response was missing required identifiers.');
  }
  return race as unknown as Race;
}

function parseRacePage(value: unknown): RacePage {
  const page = parseRequiredRecord(value, 'Race list response was malformed.');
  if (!Array.isArray(page.content)) throw new Error('Race list response was malformed.');

  return {
    content: page.content.map(parseRace),
    number: parseFiniteInteger(page.number, 'Race list page number was malformed.'),
    size: parseFiniteInteger(page.size, 'Race list page size was malformed.', 1),
    totalElements: parseFiniteInteger(page.totalElements, 'Race list total was malformed.'),
    totalPages: parseFiniteInteger(page.totalPages, 'Race list page count was malformed.'),
  };
}

function parseRecordArray<T>(value: unknown, message: string): T[] {
  if (!Array.isArray(value) || value.some((item) => !isRecord(item))) throw new Error(message);
  return value as T[];
}

function parseCheckpointArray(value: unknown): BackendCheckpointDto[] {
  const checkpoints = parseRecordArray<BackendCheckpointDto>(value, 'Checkpoints response was malformed.');
  checkpoints.forEach((checkpoint) => {
    if (typeof checkpoint.id !== 'string' || !checkpoint.id.trim()
      || typeof checkpoint.raceId !== 'string' || !checkpoint.raceId.trim()
      || typeof checkpoint.name !== 'string' || !checkpoint.name.trim()) {
      throw new Error('Checkpoints response was missing required identifiers.');
    }
  });
  return checkpoints;
}

function parseRegistration(value: unknown): Registration {
  const registration = parseRequiredRecord(value, 'Registration response was malformed.');
  if (typeof registration.id !== 'string' || !registration.id.trim()
    || typeof registration.raceId !== 'string' || !registration.raceId.trim()
    || typeof registration.status !== 'string' || !registration.status.trim()) {
    throw new Error('Registration response was missing required fields.');
  }
  return registration as unknown as Registration;
}

function parsePublicAthletes(value: unknown): PublicAthleteSummary[] {
  return parseRecordArray<Record<string, unknown>>(value, 'Athletes response was malformed.')
    .filter((row) => typeof row.participantId === 'string' && row.participantId.length > 0)
    .map((row) => ({
      ...row,
      distanceRemaining: row.distanceRemaining === null || row.distanceRemaining === undefined
        ? null : Number(row.distanceRemaining),
      rank: typeof row.rank === 'number' ? row.rank : null,
    }) as PublicAthleteSummary);
}

export function parseHeatCells(value: unknown, limit: number): HeatCell[] {
  return parseRecordArray<Record<string, unknown>>(value, 'Race heatmap response was malformed.')
    .map((cell) => ({ latitude: Number(cell.latitude), longitude: Number(cell.longitude), weight: Number(cell.weight) }))
    .filter((cell) => isValidCoordinate(cell) && Number.isFinite(cell.weight) && cell.weight > 0)
    .sort((a, b) => b.weight - a.weight)
    .slice(0, limit);
}

export function parsePublicRaceMedia(value: unknown): PublicRaceMedia[] {
  return parseRecordArray<Record<string, unknown>>(value, 'Race media response was malformed.')
    .flatMap((item) => {
      const id = optionalString(item.id);
      if (!id || item.publiclyVisible === false) return [];
      return [{
        id,
        mediaType: item.mediaType === 'VIDEO' ? 'VIDEO' : 'IMAGE',
        caption: optionalString(item.caption),
        hasThumbnail: optionalString(item.thumbnailUrl) !== null,
      } satisfies PublicRaceMedia];
    });
}

function parseResultQuery(value: unknown): ResultQuery {
  const query = parseRequiredRecord(value, 'Result query response was malformed.');
  if (typeof query.id !== 'string' || typeof query.raceId !== 'string' || typeof query.status !== 'string') {
    throw new Error('Result query response was missing required fields.');
  }
  return query as unknown as ResultQuery;
}

export function certificateUrl(result: Pick<RaceResult, 'raceId' | 'participantId' | 'certificateToken'>): string | null {
  if (!result.certificateToken) return null;
  const path = buildContractPath(API_CONTRACT.endpoints.publicRaceResultCertificate, {
    raceId: result.raceId,
    participantId: result.participantId,
  });
  return `${API_BASE_URL}${path}?token=${encodeURIComponent(result.certificateToken)}`;
}

export function publicRaceMediaUrl(raceId: string, mediaId: string, variant: 'content' | 'thumbnail'): string {
  const path = variant === 'thumbnail'
    ? buildContractPath(API_CONTRACT.endpoints.publicRaceMediaThumbnail, { raceId, mediaId })
    : buildContractPath(API_CONTRACT.endpoints.publicRaceMediaContent, { raceId, mediaId });
  return `${API_BASE_URL}${path}`;
}

function parseRaceResult(value: unknown): RaceResult {
  const result = parseRequiredRecord(value, 'Race result response was malformed.');
  if (typeof result.raceId !== 'string' || !result.raceId.trim()
    || typeof result.participantId !== 'string' || !result.participantId.trim()) {
    throw new Error('Race result response was missing required identifiers.');
  }
  return result as unknown as RaceResult;
}

function parseRaceResultPage(value: unknown): RaceResultPage {
  const page = parseRequiredRecord(value, 'Race results response was malformed.');
  if (!Array.isArray(page.items)) throw new Error('Race results response was malformed.');
  return {
    items: page.items.map(parseRaceResult),
    page: parseFiniteInteger(page.page, 'Race results page number was malformed.'),
    size: parseFiniteInteger(page.size, 'Race results page size was malformed.', 1),
    totalElements: parseFiniteInteger(page.totalElements, 'Race results total was malformed.'),
    totalPages: parseFiniteInteger(page.totalPages, 'Race results page count was malformed.'),
    revision: parseFiniteInteger(page.revision, 'Race results revision was malformed.'),
  };
}

function parseFavorite(value: unknown): FavoriteAthlete {
  const favorite = parseRequiredRecord(value, 'Favorites response was malformed.');

  return {
    raceId: parseRequiredString(favorite.raceId, 'Favorites response is missing required race or athlete identifiers.'),
    participantId: parseRequiredString(favorite.participantId, 'Favorites response is missing required race or athlete identifiers.'),
    raceName: parseOptionalString(favorite.raceName, 'Favorites response contained an invalid race name.'),
    participantName: parseOptionalString(favorite.participantName, 'Favorites response contained an invalid participant name.'),
    bibNumber: parseOptionalString(favorite.bibNumber, 'Favorites response contained an invalid bib number.'),
    status: parseOptionalString(favorite.status, 'Favorites response contained an invalid status.'),
    lastCheckpoint: parseOptionalString(favorite.lastCheckpoint, 'Favorites response contained an invalid checkpoint.'),
    distanceRemaining: typeof favorite.distanceRemaining === 'number' && Number.isFinite(favorite.distanceRemaining)
      ? favorite.distanceRemaining
      : undefined,
  };
}

function parsePublicRaceResultPage(value: unknown): PublicRaceResultPage {
  const page = parseRequiredRecord(value, 'Public race results response was malformed.');
  if (!Array.isArray(page.items)) throw new Error('Public race results response was malformed.');

  const items = page.items.map((item) => {
    const result = parseRequiredRecord(item, 'Public race results response was malformed.');
    return {
      participantId: parseRequiredString(result.participantId, 'Public race results response is missing participant identifiers.'),
      participantName: parseOptionalString(result.participantName, 'Public race results response contained an invalid participant name.'),
      bibNumber: parseOptionalString(result.bibNumber, 'Public race results response contained an invalid bib number.'),
      status: parseOptionalString(result.status, 'Public race results response contained an invalid status.'),
      totalTime: parseOptionalString(result.totalTime, 'Public race results response contained an invalid total time.'),
      category: parseOptionalString(result.category, 'Public race results response contained an invalid category.'),
      overallPosition: typeof result.overallPosition === 'number' && Number.isInteger(result.overallPosition)
        ? result.overallPosition
        : undefined,
    };
  });

  return {
    items,
    page: parseFiniteInteger(page.page, 'Public race results page number was malformed.'),
    size: parseFiniteInteger(page.size, 'Public race results page size was malformed.', 1),
    totalElements: parseFiniteInteger(page.totalElements, 'Public race results total was malformed.'),
    totalPages: parseFiniteInteger(page.totalPages, 'Public race results page count was malformed.'),
    revision: parseFiniteInteger(page.revision, 'Public race results revision was malformed.'),
  };
}

function buildContractPath(
  template: string,
  params: Record<string, string | number>,
  query?: Record<string, string | number | null | undefined>,
) {
  const placeholders = [...template.matchAll(/:([A-Za-z][A-Za-z0-9_]*)/g)].map(([, key]) => key);
  const providedKeys = Object.keys(params);
  if (placeholders.length !== providedKeys.length || placeholders.some((key) => !providedKeys.includes(key))) {
    throw new Error(`Contract path parameters do not match template: ${template}`);
  }

  for (const [key, value] of Object.entries(params)) {
    if (typeof value === 'string' && !getNonEmptyRouteId(value)) {
      throw new Error(`${key} is required.`);
    }
  }

  const path = placeholders.reduce(
    (value, key) => value.replace(`:${key}`, encodeURIComponent(String(params[key]))),
    template,
  );
  if (/:[A-Za-z][A-Za-z0-9_]*/.test(path)) {
    throw new Error(`Contract path contains an unresolved parameter: ${template}`);
  }

  const searchParams = new URLSearchParams();
  for (const [key, value] of Object.entries(query ?? {})) {
    if (value !== null && value !== undefined) {
      if (typeof value === 'string' && !getNonEmptyRouteId(value)) {
        throw new Error(`${key} is required.`);
      }
      searchParams.set(key, String(value));
    }
  }

  const search = searchParams.toString();
  return search ? `${path}?${search}` : path;
}

class ApiClient {
  private refreshPromise: Promise<boolean> | null = null;

  private async getHeaders(): Promise<HeadersInit> {
    let token = await SecureStore.getItemAsync('auth_token');
    if (!token) {
      token = await AsyncStorage.getItem('auth_token');
      if (token) {
        await SecureStore.setItemAsync('auth_token', token);
        await AsyncStorage.removeItem('auth_token');
      }
    }
    const installId = await getInstallId();
    return {
      'Content-Type': 'application/json',
      [API_CONTRACT.headers.publicClientId]: installId,
      ...(appIdentity.version ? { 'X-App-Version': appIdentity.version } : {}),
      ...(appIdentity.platform ? { 'X-App-Platform': appIdentity.platform } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    };
  }

  private async request<T>(
    endpoint: string,
    options: RequestInit = {},
    allowRefresh = true,
  ): Promise<T> {
    const headers = await this.getHeaders();
    let response: Response;
    try {
      response = await fetch(`${API_BASE_URL}${endpoint}`, {
        ...options,
        headers: { ...headers, ...options.headers },
      });
    } catch {
      throw new ApiRequestError('Network request failed', undefined, true);
    }

    if (response.status === 401 && allowRefresh && !endpoint.startsWith('/auth/')) {
      let refreshed = false;
      try {
        refreshed = await this.refreshSession();
      } catch {
        refreshed = false;
      }
      if (refreshed) {
        return this.request<T>(endpoint, options, false);
      }
      try {
        await sessionInvalidatedHandler?.();
      } finally {
        await this.clearStoredSession();
      }
    }

    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Request failed' }));
      if (response.status === 426 && error.code === APP_UPDATE_REQUIRED) {
        updateRequiredHandler?.();
      }
      throw new ApiRequestError(
        error.message || `HTTP error! status: ${response.status}`,
        response.status,
        false,
        typeof error.code === 'string' ? error.code : undefined,
      );
    }

    const text = await response.text();
    if (!text) {
      return {} as T;
    }

    return JSON.parse(text) as T;
  }

  private async refreshSession(): Promise<boolean> {
    if (this.refreshPromise) {
      return this.refreshPromise;
    }

    this.refreshPromise = (async () => {
      let refreshToken = await SecureStore.getItemAsync('refresh_token');
      if (!refreshToken) {
        refreshToken = await AsyncStorage.getItem('refresh_token');
        if (refreshToken) {
          await SecureStore.setItemAsync('refresh_token', refreshToken);
          await AsyncStorage.removeItem('refresh_token');
        }
      }
      if (!refreshToken) return false;

      const response = await fetch(`${API_BASE_URL}${API_CONTRACT.endpoints.authRefresh}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });
      if (!response.ok) throw new Error('Session refresh failed');

      const session = parseAuthResponse(await response.json());
      if (!session.token || !session.refreshToken) throw new Error('Session refresh returned an invalid session');
      await SecureStore.setItemAsync('auth_token', session.token);
      await SecureStore.setItemAsync('refresh_token', session.refreshToken);
      return true;
    })();

    try {
      return await this.refreshPromise;
    } finally {
      this.refreshPromise = null;
    }
  }

  private async clearStoredSession(): Promise<void> {
    await SecureStore.deleteItemAsync('auth_token');
    await SecureStore.deleteItemAsync('refresh_token');
    await AsyncStorage.removeItem('auth_token');
    await AsyncStorage.removeItem('refresh_token');
    await AsyncStorage.removeItem('user');
  }

  // Auth endpoints
  async getAppConfig(): Promise<AppConfig> {
    return parseAppConfig(await this.request<unknown>(API_CONTRACT.endpoints.publicAppConfig, {}, false));
  }

  async login(email: string, password: string): Promise<AuthResponse> {
    const raw = await this.request<unknown>(API_CONTRACT.endpoints.authLogin, {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    });
    if (raw && typeof raw === 'object' && (raw as Record<string, unknown>).mfaRequired === true) {
      throw new ApiRequestError('Administrator accounts sign in on the web dashboard', 403, false, 'ADMIN_MFA_WEB_ONLY');
    }
    const response = parseAuthResponse(raw);
    if (response.token && response.refreshToken) {
      await SecureStore.setItemAsync('auth_token', response.token);
      await SecureStore.setItemAsync('refresh_token', response.refreshToken);
      return response;
    }
    if (response.requiresEmailVerification) {
      return response;
    }
    if (!response.token || !response.refreshToken) {
      throw new Error('Login did not return a valid session');
    }
    return response;
  }

  async register(userData: RegisterRequest): Promise<AuthResponse> {
    const payload: RegisterRequest = {
      firstName: userData.firstName.trim(),
      lastName: userData.lastName.trim(),
      email: userData.email.trim(),
      password: userData.password,
      phone: userData.phone?.trim() || undefined,
      privacyPolicyAccepted: userData.privacyPolicyAccepted,
      privacyPolicyVersion: userData.privacyPolicyVersion,
      termsAccepted: userData.termsAccepted,
      termsVersion: userData.termsVersion,
      experiences: userData.experiences,
    };

    const response = parseAuthResponse(await this.request<unknown>(API_CONTRACT.endpoints.authRegister, {
      method: 'POST',
      body: JSON.stringify(payload),
    }));

    if (response.token && response.refreshToken) {
      await SecureStore.setItemAsync('auth_token', response.token);
      await SecureStore.setItemAsync('refresh_token', response.refreshToken);
    } else {
      await this.clearStoredSession();
    }

    return response;
  }

  async logout(): Promise<void> {
    await this.clearStoredSession();
  }

  async getCurrentUser(): Promise<User> {
    return parseUser(await this.request<unknown>(API_CONTRACT.endpoints.authMe));
  }

  async updateProfile(data: ProfileUpdateRequest): Promise<User> {
    return parseUser(await this.request<unknown>(API_CONTRACT.endpoints.authProfile, {
      method: 'PUT',
      body: JSON.stringify(data),
    }));
  }

  async updateCapabilities(data: CapabilityUpdateRequest): Promise<User> {
    return parseUser(await this.request<unknown>(API_CONTRACT.endpoints.authProfileCapabilities, {
      method: 'PUT',
      body: JSON.stringify(data),
    }));
  }

  async acceptLegalDocuments(): Promise<{ privacyPolicyVersion: string; termsVersion: string }> {
    const response = await this.request<unknown>(API_CONTRACT.endpoints.myLegalAcceptance, {
      method: 'POST',
      body: JSON.stringify({
        privacyPolicyVersion: API_CONTRACT.legal.privacyPolicyVersion,
        termsVersion: API_CONTRACT.legal.termsVersion,
      }),
    });
    if (!response || typeof response !== 'object') {
      throw new Error('Legal acceptance response was invalid.');
    }
    const record = response as Record<string, unknown>;
    return {
      privacyPolicyVersion: parseRequiredString(record.privacyPolicyVersion, 'Legal acceptance response contained an invalid privacy policy version.'),
      termsVersion: parseRequiredString(record.termsVersion, 'Legal acceptance response contained an invalid terms version.'),
    };
  }

  // Race endpoints
  async getRaces(params?: { status?: string; upcoming?: boolean }): Promise<Race[]> {
    const firstPage = await this.getRacesPage(0, 50, params);
    const races = [...firstPage.content];
    for (let pageNumber = firstPage.number + 1; pageNumber < firstPage.totalPages; pageNumber += 1) {
      const page = await this.getRacesPage(pageNumber, firstPage.size, params);
      races.push(...page.content);
    }
    return races;
  }

  async getRacesPage(
    page = 0,
    size = 50,
    params?: { status?: string; upcoming?: boolean },
  ): Promise<RacePage> {
    const searchParams: Record<string, string | number> = { page, size };
    if (params?.status) searchParams.status = params.status;
    if (params?.upcoming) searchParams.upcoming = 'true';
    const response = await this.request<unknown>(buildContractPath(
      API_CONTRACT.endpoints.racesList,
      {},
      searchParams,
    ));
    return parseRacePage(response);
  }

  async getRace(id: string): Promise<Race> {
    const response = await this.request<unknown>(buildContractPath(API_CONTRACT.endpoints.raceById, { id }));
    return parseRace(response);
  }

  async getRaceCheckpoints(raceId: string): Promise<BackendCheckpointDto[]> {
    const response = await this.request<unknown>(buildContractPath(API_CONTRACT.endpoints.raceCheckpoints, { raceId }));
    return parseCheckpointArray(response);
  }

  async getAssignedCheckpoints(): Promise<AssignedCheckpoint[]> {
    const rows: AssignedCheckpoint[] = [];
    let pageNumber = 0;
    let totalPages: number;
    let totalElements: number | undefined;
    do {
      const response = await this.request<unknown>(buildContractPath(
        API_CONTRACT.endpoints.operationsCheckpoints, {}, { page: pageNumber, size: 50 },
      ));
      const page = parseAssignedCheckpointPage(response);
      if (page.number !== pageNumber) throw new Error('Assigned checkpoint page number did not match request.');
      if (page.size !== 50 || (totalElements !== undefined && page.totalElements !== totalElements)) {
        throw new Error('Assigned checkpoint pagination changed during loading.');
      }
      totalElements = page.totalElements;
      rows.push(...page.content);
      totalPages = page.totalPages;
      pageNumber += 1;
    } while (pageNumber < totalPages);
    if (rows.length !== totalElements) throw new Error('Assigned checkpoint list was incomplete.');
    return rows;
  }

  async reportCheckpointEmergency(raceId: string, checkpointId: string, message: string): Promise<CheckpointEmergencyReceipt> {
    const response = await this.request<unknown>(buildContractPath(
      API_CONTRACT.endpoints.checkpointEmergency, { raceId, checkpointId },
    ), { method: 'POST', body: JSON.stringify({ message }) });
    return parseCheckpointEmergencyReceipt(response);
  }

  async getPublicRaceResults(raceId: string, page = 0, size = 50, revision?: number): Promise<PublicRaceResultPage> {
    const response = await this.request<unknown>(buildContractPath(
      API_CONTRACT.endpoints.publicRaceResults,
      { raceId },
      { page, size, revision },
    ));
    return parsePublicRaceResultPage(response);
  }

  async getMyRaces(): Promise<Registration[]> {
    const response = await this.request<unknown>(API_CONTRACT.endpoints.myRegistrations);
    return parseRecordArray<Registration>(response, 'Registrations response was malformed.').map(parseRegistration);
  }

  // Spectator follow endpoints
  async listFavorites(): Promise<FavoriteAthlete[]> {
    const response = await this.request<unknown>(API_CONTRACT.endpoints.favorites);
    if (!Array.isArray(response)) {
      throw new Error('Favorites response was malformed.');
    }
    return response.map(parseFavorite);
  }

  async followAthlete(raceId: string, participantId: string): Promise<FavoriteAthlete> {
    const safeRaceId = requireNonEmptyId(raceId, 'raceId');
    const safeParticipantId = requireNonEmptyId(participantId, 'participantId');
    const response = await this.request<unknown>(API_CONTRACT.endpoints.favorites, {
      method: 'POST',
      body: JSON.stringify({ raceId: safeRaceId, participantId: safeParticipantId }),
    });
    return parseFavorite(response);
  }

  async unfollowAthlete(raceId: string, participantId: string): Promise<void> {
    const safeParticipantId = requireNonEmptyId(participantId, 'participantId');
    await this.request(buildContractPath(
      API_CONTRACT.endpoints.favoriteDelete,
      { participantId: safeParticipantId },
      { raceId },
    ), { method: 'DELETE' });
  }

  // Registration endpoints
  async getRegistrationFields(raceId: string): Promise<RegistrationField[]> {
    const response = await this.request<unknown>(buildContractPath(API_CONTRACT.endpoints.publicRaceRegistrationFields, { raceId }));
    const fields = parseRecordArray<RegistrationField>(response, 'Registration fields response was malformed.');
    fields.forEach((field) => {
      if (typeof field.id !== 'string' || !field.id.trim() || typeof field.label !== 'string') {
        throw new Error('Registration fields response was missing required fields.');
      }
    });
    return fields;
  }

  async registerForRace(
    raceId: string,
    data: RaceRegistrationRequest,
  ): Promise<Registration> {
    const response = await this.request<unknown>(buildContractPath(API_CONTRACT.endpoints.raceRegister, { id: raceId }), {
      method: 'POST',
      body: JSON.stringify(data),
    });
    return parseRegistration(response);
  }

  async cancelRegistration(registrationId: string): Promise<void> {
    return this.request<void>(buildContractPath(API_CONTRACT.endpoints.registrationDelete, { registrationId }), {
      method: 'DELETE',
    });
  }

  async updateMediaConsent(participantId: string, consented: boolean): Promise<{ participantId: string; consented: boolean }> {
    return this.request(buildContractPath(API_CONTRACT.endpoints.myRegistrationMediaConsent, { participantId }), {
      method: 'PUT',
      body: JSON.stringify({ consented }),
    });
  }

  async updateLocationSharing(participantId: string, enabled: boolean): Promise<{ participantId: string; enabled: boolean }> {
    return this.request(buildContractPath(API_CONTRACT.endpoints.myRegistrationLocationSharing, { participantId }), {
      method: 'PUT',
      body: JSON.stringify({ enabled }),
    });
  }

  // Tracking endpoints
  async sendLocation(raceId: string, location: GpsLocation): Promise<void> {
    return this.request<void>(buildContractPath(API_CONTRACT.endpoints.trackingLocation, { raceId }), {
      method: 'POST',
      body: JSON.stringify(location),
    });
  }

  async sendLocationsBatch(raceId: string, locations: GpsLocation[]): Promise<void> {
    return this.request<void>(buildContractPath(API_CONTRACT.endpoints.trackingLocations, { raceId }), {
      method: 'POST',
      body: JSON.stringify({ locations }),
    });
  }

  async recordCheckpointTime(
    raceId: string,
    checkpointId: string,
    participantBibNumber: string,
    method: 'MANUAL' | 'QR_CODE' | 'RFID' = 'MANUAL',
    capturedOfflineAt?: string,
  ): Promise<void> {
    return this.request<void>(buildContractPath(API_CONTRACT.endpoints.checkpointRecord, { raceId, checkpointId }), {
      method: 'POST',
      body: JSON.stringify({ participantBibNumber, method, capturedOfflineAt }),
    });
  }

  async qrCheckIn(raceId: string, code: string): Promise<StaffParticipant> {
    return this.request<StaffParticipant>(buildContractPath(API_CONTRACT.endpoints.raceParticipantQrCheckIn, { raceId }), {
      method: 'POST',
      body: JSON.stringify({ code }),
    });
  }

  async checkInParticipant(raceId: string, participantId: string): Promise<StaffParticipant> {
    return this.request<StaffParticipant>(
      buildContractPath(API_CONTRACT.endpoints.raceParticipantCheckIn, { raceId, id: participantId }),
      { method: 'POST' },
    );
  }

  async searchParticipants(raceId: string, query: string): Promise<StaffParticipant[]> {
    const path = buildContractPath(API_CONTRACT.endpoints.raceParticipantSearch, { raceId });
    const response = await this.request<unknown>(`${path}?q=${encodeURIComponent(query.trim())}`);
    return parseRecordArray<StaffParticipant>(response, 'Participant search response was malformed.');
  }

  async getRecentStationRecords(raceId: string, checkpointId: string, limit = 20): Promise<StationRecord[]> {
    const path = buildContractPath(API_CONTRACT.endpoints.checkpointRecords, { raceId, checkpointId });
    const response = await this.request<unknown>(`${path}?limit=${limit}`);
    return parseRecordArray<StationRecord>(response, 'Checkpoint records response was malformed.');
  }

  async undoStationRecord(raceId: string, checkpointId: string, recordId: string, reason: string): Promise<void> {
    await this.request(buildContractPath(API_CONTRACT.endpoints.checkpointRecordVoid, { raceId, checkpointId, recordId }), {
      method: 'POST',
      body: JSON.stringify({ reason }),
    });
  }

  async getRaceWaves(raceId: string): Promise<RaceWave[]> {
    const response = await this.request<unknown>(buildContractPath(API_CONTRACT.endpoints.raceWaves, { raceId }));
    return parseRecordArray<RaceWave>(response, 'Waves response was malformed.');
  }

  async startWave(raceId: string, waveId: string): Promise<RaceWave> {
    return this.request<RaceWave>(buildContractPath(API_CONTRACT.endpoints.raceWaveStart, { raceId, waveId }), {
      method: 'POST',
    });
  }

  async recordCheckpoint(
    raceId: string,
    checkpointId: string,
    scanToken: string,
    location?: GpsLocation,
  ): Promise<SelfScanOutcome> {
    const response = await this.request<unknown>(buildContractPath(API_CONTRACT.endpoints.trackingCheckpoint, { raceId }), {
      method: 'POST',
      body: JSON.stringify({ checkpointId, method: 'qr_code', scanToken, location }),
    });
    return parseSelfScanOutcome(response);
  }

  // Results endpoints
  async getRaceResults(raceId: string): Promise<RaceResult[]> {
    const response = await this.request<unknown>(buildContractPath(API_CONTRACT.endpoints.raceResults, { raceId }));
    return parseRecordArray<RaceResult>(response, 'Race results response was malformed.').map(parseRaceResult);
  }

  async getMyResults(): Promise<RaceResult[]> {
    const page = await this.getMyResultsPage(0, 100);
    return page.items;
  }

  async getMyResultsPage(page = 0, size = 50): Promise<RaceResultPage> {
    const response = await this.request<unknown>(buildContractPath(API_CONTRACT.endpoints.myResultsPage, {}, { page, size }));
    return parseRaceResultPage(response);
  }

  async getResult(raceId: string, participantId: string): Promise<RaceResult> {
    const response = await this.request<unknown>(buildContractPath(API_CONTRACT.endpoints.raceResultByParticipant, { raceId, participantId }));
    return parseRaceResult(response);
  }

  // Notifications
  async getNotifications(): Promise<Notification[]> {
    const response = await this.request<unknown>(API_CONTRACT.endpoints.notifications);
    return parseRecordArray<Notification>(response, 'Notifications response was malformed.').map(parseNotification);
  }

  async markNotificationRead(id: string): Promise<void> {
    return this.request<void>(buildContractPath(API_CONTRACT.endpoints.notificationRead, { id: requireNonEmptyId(id, 'id') }), {
      method: 'PUT',
    });
  }

  async registerDevice(token: string, platform?: string): Promise<void> {
    return this.request<void>(API_CONTRACT.endpoints.notificationDevices, {
      method: 'POST',
      body: JSON.stringify({ token: requireNonEmptyId(token, 'token'), platform }),
    });
  }

  async unregisterDevice(token: string): Promise<void> {
    return this.request<void>(buildContractPath(API_CONTRACT.endpoints.notificationDevices, {}, { token }), {
      method: 'DELETE',
    }, false);
  }

  async reportEmergency(
    raceId: string,
    payload: { latitude?: number; longitude?: number; message?: string },
  ): Promise<{ deliveryStatus?: string }> {
    return this.request<{ deliveryStatus?: string }>(
      buildContractPath(API_CONTRACT.endpoints.raceEmergency, { raceId }), {
        method: 'POST',
        body: JSON.stringify(payload),
      },
    );
  }

  async deletionStatus(): Promise<DeletionStatus> {
    return parseDeletionStatus(await this.request<unknown>(API_CONTRACT.endpoints.myDeletionStatus));
  }

  async cancelDeletion(): Promise<DeletionStatus> {
    return parseDeletionStatus(await this.request<unknown>(API_CONTRACT.endpoints.myDeletionCancel, { method: 'POST' }));
  }

  async forgotPassword(email: string): Promise<void> {
    await this.request(API_CONTRACT.endpoints.authForgotPassword, {
      method: 'POST',
      body: JSON.stringify({ email }),
    }, false);
  }

  async verifyEmail(token: string): Promise<void> {
    await this.request(`${API_CONTRACT.endpoints.authVerifyEmail}?token=${encodeURIComponent(token)}`, {}, false);
  }

  async resetPassword(token: string, newPassword: string): Promise<void> {
    await this.request(API_CONTRACT.endpoints.authResetPassword, {
      method: 'POST',
      body: JSON.stringify({ token, newPassword }),
    }, false);
  }

  async resendVerification(email: string): Promise<void> {
    await this.request(API_CONTRACT.endpoints.authResendVerification, {
      method: 'POST',
      body: JSON.stringify({ email }),
    }, false);
  }

  async startPaymentCheckout(raceId: string, participantId: string, promoCode?: string): Promise<PaymentCheckout> {
    const path = buildContractPath(API_CONTRACT.endpoints.raceParticipantPaymentCheckout, { raceId, id: participantId });
    const query = promoCode?.trim() ? `?promoCode=${encodeURIComponent(promoCode.trim())}` : '';
    const checkout = parseRequiredRecord(await this.request<unknown>(`${path}${query}`, { method: 'POST' }),
      'Payment response was malformed.');
    return {
      ...checkout,
      checkoutUrl: parseOptionalString(checkout.checkoutUrl, 'Payment checkout link was malformed.') ?? null,
    } as PaymentCheckout;
  }

  async getMyHistory(): Promise<AthleteHistoryEntry[]> {
    const response = await this.request<unknown>(API_CONTRACT.endpoints.myResultsHistory);
    return parseRecordArray<AthleteHistoryEntry>(response, 'Race history response was malformed.');
  }

  async getRaceLeaderboard(raceId: string, limit = 50): Promise<PublicAthleteSummary[]> {
    const path = buildContractPath(API_CONTRACT.endpoints.publicRaceLeaderboard, { raceId });
    return parsePublicAthletes(await this.request<unknown>(`${path}?limit=${limit}`));
  }

  async searchRaceAthletes(raceId: string, query: string): Promise<PublicAthleteSummary[]> {
    const path = buildContractPath(API_CONTRACT.endpoints.publicRaceSearch, { raceId });
    return parsePublicAthletes(await this.request<unknown>(`${path}?q=${encodeURIComponent(query.trim())}`));
  }

  async getPublicRaceMedia(raceId: string): Promise<PublicRaceMedia[]> {
    return parsePublicRaceMedia(await this.request<unknown>(
      buildContractPath(API_CONTRACT.endpoints.publicRaceMedia, { raceId }),
    ));
  }

  async getRaceHeatmap(raceId: string, since: Date, limit: number): Promise<HeatCell[]> {
    const path = buildContractPath(API_CONTRACT.endpoints.publicRaceHeatmap, { raceId });
    return parseHeatCells(await this.request<unknown>(`${path}?from=${encodeURIComponent(since.toISOString())}`), limit);
  }

  async getPublicRaceRoute(raceId: string): Promise<Coordinate[]> {
    const response = await this.request<unknown>(buildContractPath(API_CONTRACT.endpoints.publicRaceRoute, { raceId }));
    const points = parseRecordArray<Record<string, unknown>>(response, 'Race route response was malformed.')
      .sort((a, b) => Number(a.sequence ?? 0) - Number(b.sequence ?? 0));
    return toCoordinates(points, (point) => ({ latitude: point.latitude, longitude: point.longitude }));
  }

  async getPublicAthleteTracking(raceId: string, participantId: string): Promise<PublicAthleteTracking> {
    const raw = parseRequiredRecord(await this.request<unknown>(
      buildContractPath(API_CONTRACT.endpoints.publicAthleteTracking, { raceId, participantId }),
    ), 'Athlete tracking response was malformed.');
    return { ...raw, splits: Array.isArray(raw.splits) ? raw.splits : [] } as unknown as PublicAthleteTracking;
  }

  async getPublicAthleteTrack(raceId: string, participantId: string, since?: string | null): Promise<TrackPoint[]> {
    const path = buildContractPath(API_CONTRACT.endpoints.publicAthleteTrack, { raceId, participantId });
    const query = since ? `?since=${encodeURIComponent(since)}` : '';
    const raw = parseRequiredRecord(await this.request<unknown>(`${path}${query}`), 'Athlete track response was malformed.');
    const points = Array.isArray(raw.points) ? raw.points as Record<string, unknown>[] : [];
    return points
      .map((point) => ({
        latitude: Number(point.lat),
        longitude: Number(point.lng),
        time: typeof point.time === 'string' ? point.time : null,
      }))
      .filter((point) => Number.isFinite(point.latitude) && Number.isFinite(point.longitude)
        && !(point.latitude === 0 && point.longitude === 0));
  }

  async getNotificationPreferences(): Promise<NotificationPreferences> {
    return parseNotificationPreferences(await this.request<unknown>(API_CONTRACT.endpoints.notificationPreferences));
  }

  async updateNotificationPreferences(preferences: NotificationPreferences): Promise<NotificationPreferences> {
    return parseNotificationPreferences(await this.request<unknown>(API_CONTRACT.endpoints.notificationPreferences, {
      method: 'PUT',
      body: JSON.stringify(preferences),
    }));
  }

  async getConsent(): Promise<ConsentState> {
    const raw = parseRequiredRecord(await this.request<unknown>(API_CONTRACT.endpoints.myConsent), 'Consent response was malformed.');
    return {
      dataProcessingConsentAt: typeof raw.dataProcessingConsentAt === 'string' ? raw.dataProcessingConsentAt : null,
      marketingConsent: raw.marketingConsent === true,
    };
  }

  async updateMarketingConsent(marketingConsent: boolean): Promise<ConsentState> {
    const raw = parseRequiredRecord(await this.request<unknown>(API_CONTRACT.endpoints.myConsent, {
      method: 'PUT',
      body: JSON.stringify({ marketingConsent }),
    }), 'Consent response was malformed.');
    return {
      dataProcessingConsentAt: typeof raw.dataProcessingConsentAt === 'string' ? raw.dataProcessingConsentAt : null,
      marketingConsent: raw.marketingConsent === true,
    };
  }

  async exportMyData(): Promise<string> {
    const data = await this.request<unknown>(API_CONTRACT.endpoints.myDataExport);
    return JSON.stringify(data, null, 2);
  }

  async getMyPersonalBests(): Promise<PersonalBest[]> {
    const response = await this.request<unknown>(API_CONTRACT.endpoints.myPersonalBests);
    return parseRecordArray<Record<string, unknown>>(response, 'Personal bests response was malformed.')
      .map((row) => ({
        distanceKm: Number(row.distanceKm),
        chipTime: typeof row.chipTime === 'string' ? row.chipTime : null,
        raceId: typeof row.raceId === 'string' ? row.raceId : null,
        raceName: typeof row.raceName === 'string' ? row.raceName : null,
        resultCount: Number(row.resultCount ?? 0),
      }))
      .filter((row) => Number.isFinite(row.distanceKm) && row.distanceKm > 0);
  }

  async submitResultQuery(raceId: string, message: string, checkpointId?: string): Promise<ResultQuery> {
    return parseResultQuery(await this.request<unknown>(buildContractPath(API_CONTRACT.endpoints.myResultQuery, { raceId }), {
      method: 'POST',
      body: JSON.stringify({ message, ...(checkpointId ? { checkpointId } : {}) }),
    }));
  }

  async getMyResultQueries(): Promise<ResultQuery[]> {
    const response = await this.request<unknown>(API_CONTRACT.endpoints.myResultQueries);
    return parseRecordArray<ResultQuery>(response, 'Result queries response was malformed.').map(parseResultQuery);
  }

  async cancelDeletionWithPassword(email: string, password: string): Promise<void> {
    await this.request(API_CONTRACT.endpoints.authDeletionCancel, {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    });
  }

  async deleteAccount(password: string, confirmation: string): Promise<DeletionStatus> {
    return parseDeletionStatus(await this.request<unknown>(API_CONTRACT.endpoints.myAccount, {
      method: 'DELETE',
      body: JSON.stringify({ password, confirmation }),
    }));
  }
}

export const api = new ApiClient();
