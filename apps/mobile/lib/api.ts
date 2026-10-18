import AsyncStorage from '@react-native-async-storage/async-storage';
import * as SecureStore from 'expo-secure-store';
import type {
  AuthResponse,
  RegisterRequest,
  User,
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
} from '@/types';
import { API_CONTRACT, getVersionedApiBase } from '@race-tracker/contracts';

const API_ROOT = process.env.EXPO_PUBLIC_API_URL || API_CONTRACT.mobile.defaultApiRoot;
const API_BASE_URL = getVersionedApiBase(API_ROOT);

type PageResponse<T> = {
  content: T[];
};

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
    const response = await fetch(`${API_BASE_URL}${endpoint}`, {
      ...options,
      headers: { ...headers, ...options.headers },
    });

    if (response.status === 401 && allowRefresh && !endpoint.startsWith('/auth/')) {
      try {
        if (await this.refreshSession()) {
          return this.request<T>(endpoint, options, false);
        }
      } catch {
        await this.clearStoredSession();
      }
    }

    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Request failed' }));
      throw new Error(error.message || `HTTP error! status: ${response.status}`);
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

      const session = await response.json() as AuthResponse;
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
  }

  // Auth endpoints
  async login(email: string, password: string): Promise<AuthResponse> {
    const response = await this.request<AuthResponse>(API_CONTRACT.endpoints.authLogin, {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    });
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
    };

    const response = await this.request<AuthResponse>(API_CONTRACT.endpoints.authRegister, {
      method: 'POST',
      body: JSON.stringify(payload),
    });

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
    return this.request<User>(API_CONTRACT.endpoints.authMe);
  }

  async updateProfile(data: Partial<User>): Promise<User> {
    return this.request<User>(API_CONTRACT.endpoints.authProfile, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  // Race endpoints
  async getRaces(params?: { status?: string; upcoming?: boolean }): Promise<Race[]> {
    const searchParams = new URLSearchParams();
    if (params?.status) searchParams.append('status', params.status);
    if (params?.upcoming) searchParams.append('upcoming', 'true');
    const query = searchParams.toString();
    const page = await this.request<PageResponse<Race>>(buildContractPath(
      API_CONTRACT.endpoints.racesList,
      {},
      query ? Object.fromEntries(new URLSearchParams(query).entries()) : undefined,
    ));
    return page.content ?? [];
  }

  async getRace(id: string): Promise<Race> {
    return this.request<Race>(buildContractPath(API_CONTRACT.endpoints.raceById, { id }));
  }

  async getRaceCheckpoints(raceId: string): Promise<BackendCheckpointDto[]> {
    return this.request<BackendCheckpointDto[]>(buildContractPath(API_CONTRACT.endpoints.raceCheckpoints, { raceId }));
  }

  async getMyRaces(): Promise<Registration[]> {
    return this.request<Registration[]>(API_CONTRACT.endpoints.myRegistrations);
  }

  // Registration endpoints
  async getRegistrationFields(raceId: string): Promise<RegistrationField[]> {
    return this.request<RegistrationField[]>(buildContractPath(API_CONTRACT.endpoints.publicRaceRegistrationFields, { raceId }));
  }

  async registerForRace(
    raceId: string,
    data: RaceRegistrationRequest,
  ): Promise<Registration> {
    return this.request<Registration>(buildContractPath(API_CONTRACT.endpoints.raceRegister, { id: raceId }), {
      method: 'POST',
      body: JSON.stringify(data),
    });
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
    return this.request<RaceResult[]>(buildContractPath(API_CONTRACT.endpoints.raceResults, { raceId }));
  }

  async getMyResults(): Promise<RaceResult[]> {
    const page = await this.getMyResultsPage(0, 100);
    return page.items;
  }

  async getMyResultsPage(page = 0, size = 50): Promise<RaceResultPage> {
    return this.request<RaceResultPage>(buildContractPath(API_CONTRACT.endpoints.myResultsPage, {}, { page, size }));
  }

  async getResult(raceId: string, participantId: string): Promise<RaceResult> {
    return this.request<RaceResult>(buildContractPath(API_CONTRACT.endpoints.raceResultByParticipant, { raceId, participantId }));
  }

  // Notifications
  async getNotifications(): Promise<Notification[]> {
    return this.request<Notification[]>(API_CONTRACT.endpoints.notifications);
  }

  async markNotificationRead(id: string): Promise<void> {
    return this.request<void>(buildContractPath(API_CONTRACT.endpoints.notificationRead, { id }), {
      method: 'PUT',
    });
  }

  async registerDevice(token: string, platform?: string): Promise<void> {
    return this.request<void>(API_CONTRACT.endpoints.notificationDevices, {
      method: 'POST',
      body: JSON.stringify({ token, platform }),
    });
  }

  async unregisterDevice(token: string): Promise<void> {
    return this.request<void>(buildContractPath(API_CONTRACT.endpoints.notificationDevices, {}, { token }), {
      method: 'DELETE',
    });
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
    return this.request<DeletionStatus>(API_CONTRACT.endpoints.myDeletionStatus);
  }

  async cancelDeletion(): Promise<DeletionStatus> {
    return this.request<DeletionStatus>(API_CONTRACT.endpoints.myDeletionCancel, { method: 'POST' });
  }

  async deleteAccount(password: string, confirmation: string): Promise<DeletionStatus> {
    return this.request<DeletionStatus>(API_CONTRACT.endpoints.myAccount, {
      method: 'DELETE',
      body: JSON.stringify({ password, confirmation }),
    });
  }
}

export const api = new ApiClient();
