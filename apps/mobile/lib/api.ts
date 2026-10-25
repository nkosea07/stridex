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
  Notification,
  BackendCheckpointDto,
  DeletionStatus,
  UserCapability,
  UserRole,
} from '@/types';
import { normalizeUser } from './capabilities';
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

type SessionInvalidatedHandler = () => void | Promise<void>;

let sessionInvalidatedHandler: SessionInvalidatedHandler | null = null;

export function setSessionInvalidatedHandler(handler: SessionInvalidatedHandler | null): void {
  sessionInvalidatedHandler = handler;
}

export type ApiErrorKind = 'network' | 'not_found' | 'unknown';

export class ApiRequestError extends Error {
  constructor(
    message: string,
    public readonly status?: number,
    public readonly isNetworkError = false,
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

function parseDeletionStatus(value: unknown): DeletionStatus {
  const status = parseRequiredRecord(value, 'Account deletion response was malformed.');
  if (typeof status.deletionRequested !== 'boolean') {
    throw new Error('Account deletion response was missing its request state.');
  }
  return {
    deletionRequested: status.deletionRequested,
    requestedAt: status.requestedAt === null ? null : parseOptionalString(status.requestedAt, 'Account deletion response contained an invalid request date.'),
    scheduledFor: status.scheduledFor === null ? null : parseOptionalString(status.scheduledFor, 'Account deletion response contained an invalid schedule date.'),
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
    return {
      'Content-Type': 'application/json',
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
      throw new ApiRequestError(error.message || `HTTP error! status: ${response.status}`, response.status);
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
  async login(email: string, password: string): Promise<AuthResponse> {
    const response = parseAuthResponse(await this.request<unknown>(API_CONTRACT.endpoints.authLogin, {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    }));
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

  async recordCheckpoint(raceId: string, checkpointId: string, method: 'qr_code' | 'gps'): Promise<void> {
    return this.request<void>(buildContractPath(API_CONTRACT.endpoints.trackingCheckpoint, { raceId }), {
      method: 'POST',
      body: JSON.stringify({ checkpointId, method, timestamp: new Date().toISOString() }),
    });
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
    payload: { latitude: number; longitude: number; message?: string },
  ): Promise<void> {
    return this.request<void>(buildContractPath(API_CONTRACT.endpoints.raceEmergency, { raceId }), {
      method: 'POST',
      body: JSON.stringify(payload),
    });
  }

  async deletionStatus(): Promise<DeletionStatus> {
    return parseDeletionStatus(await this.request<unknown>(API_CONTRACT.endpoints.myDeletionStatus));
  }

  async cancelDeletion(): Promise<DeletionStatus> {
    return parseDeletionStatus(await this.request<unknown>(API_CONTRACT.endpoints.myDeletionCancel, { method: 'POST' }));
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
