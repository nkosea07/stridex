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
} from '@/types';
import { API_CONTRACT, getVersionedApiBase } from '@race-tracker/contracts';

const API_ROOT = process.env.EXPO_PUBLIC_API_URL || API_CONTRACT.mobile.defaultApiRoot;
const API_BASE_URL = getVersionedApiBase(API_ROOT);

type PageResponse<T> = {
  content: T[];
};

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

      const response = await fetch(`${API_BASE_URL}/auth/refresh`, {
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
    const response = await this.request<AuthResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    });
    if (response.token && response.refreshToken) {
      await SecureStore.setItemAsync('auth_token', response.token);
      await SecureStore.setItemAsync('refresh_token', response.refreshToken);
      return response;
    }
    if (response.mfaRequired && response.mfaChallengeToken) {
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

  async beginTotpEnrollment(challengeToken: string): Promise<{ secret: string; provisioningUri: string }> {
    return this.request('/auth/mfa/totp/enroll', {
      method: 'POST',
      body: JSON.stringify({ challengeToken }),
    });
  }

  async completeMfa(challengeToken: string, code: string, enrollment: boolean): Promise<AuthResponse> {
    const response = await this.request<AuthResponse>(
      enrollment ? '/auth/mfa/totp/confirm' : '/auth/mfa/verify-login',
      {
        method: 'POST',
        body: JSON.stringify({ challengeToken, code }),
      },
    );
    if (!response.token || !response.refreshToken) {
      throw new Error('MFA verification did not return a valid session');
    }
    await SecureStore.setItemAsync('auth_token', response.token);
    await SecureStore.setItemAsync('refresh_token', response.refreshToken);
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

    const response = await this.request<AuthResponse>('/auth/register', {
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
    return this.request<User>('/auth/me');
  }

  async updateProfile(data: Partial<User>): Promise<User> {
    return this.request<User>('/auth/profile', {
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
    const page = await this.request<PageResponse<Race>>(`/races${query ? `?${query}` : ''}`);
    return page.content ?? [];
  }

  async getRace(id: string): Promise<Race> {
    return this.request<Race>(`/races/${id}`);
  }

  async getRaceCheckpoints(raceId: string): Promise<BackendCheckpointDto[]> {
    return this.request<BackendCheckpointDto[]>(`/races/${raceId}/checkpoints`);
  }

  async getMyRaces(): Promise<Registration[]> {
    return this.request<Registration[]>('/participants/my-registrations');
  }

  // Registration endpoints
  async getRegistrationFields(raceId: string): Promise<RegistrationField[]> {
    return this.request<RegistrationField[]>(`/public/races/${raceId}/registration-fields`);
  }

  async registerForRace(
    raceId: string,
    data: RaceRegistrationRequest,
  ): Promise<Registration> {
    return this.request<Registration>(`/races/${raceId}/participants/register`, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async cancelRegistration(registrationId: string): Promise<void> {
    return this.request<void>(`/registrations/${registrationId}`, {
      method: 'DELETE',
    });
  }

  // Tracking endpoints
  async sendLocation(raceId: string, location: GpsLocation): Promise<void> {
    return this.request<void>(`/tracking/${raceId}/location`, {
      method: 'POST',
      body: JSON.stringify(location),
    });
  }

  async sendLocationsBatch(raceId: string, locations: GpsLocation[]): Promise<void> {
    return this.request<void>(`/tracking/${raceId}/locations`, {
      method: 'POST',
      body: JSON.stringify({ locations }),
    });
  }

  async recordCheckpoint(raceId: string, checkpointId: string, method: 'qr_code' | 'gps'): Promise<void> {
    return this.request<void>(`/tracking/${raceId}/checkpoint`, {
      method: 'POST',
      body: JSON.stringify({ checkpointId, method, timestamp: new Date().toISOString() }),
    });
  }

  // Results endpoints
  async getRaceResults(raceId: string): Promise<RaceResult[]> {
    return this.request<RaceResult[]>(`/races/${raceId}/results`);
  }

  async getMyResults(): Promise<RaceResult[]> {
    const page = await this.getMyResultsPage(0, 100);
    return page.items;
  }

  async getMyResultsPage(page = 0, size = 50): Promise<RaceResultPage> {
    return this.request<RaceResultPage>(`${API_CONTRACT.endpoints.myResultsPage}?page=${page}&size=${size}`);
  }

  async getResult(raceId: string, participantId: string): Promise<RaceResult> {
    return this.request<RaceResult>(`/races/${raceId}/results/${participantId}`);
  }

  // Notifications
  async getNotifications(): Promise<Notification[]> {
    return this.request<Notification[]>('/notifications');
  }

  async markNotificationRead(id: string): Promise<void> {
    return this.request<void>(`/notifications/${id}/read`, {
      method: 'PUT',
    });
  }
}

export const api = new ApiClient();
