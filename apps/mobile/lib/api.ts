import AsyncStorage from '@react-native-async-storage/async-storage';
import type {
  AuthResponse,
  RegisterRequest,
  User,
  Race,
  Registration,
  GpsLocation,
  RaceResult,
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
  private async getHeaders(): Promise<HeadersInit> {
    const token = await AsyncStorage.getItem('auth_token');
    return {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    };
  }

  private async request<T>(
    endpoint: string,
    options: RequestInit = {}
  ): Promise<T> {
    const headers = await this.getHeaders();
    const response = await fetch(`${API_BASE_URL}${endpoint}`, {
      ...options,
      headers: { ...headers, ...options.headers },
    });

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

  // Auth endpoints
  async login(email: string, password: string): Promise<AuthResponse> {
    const response = await this.request<AuthResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    });
    await AsyncStorage.setItem('auth_token', response.token);
    await AsyncStorage.setItem('refresh_token', response.refreshToken);
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
    await AsyncStorage.setItem('auth_token', response.token);
    await AsyncStorage.setItem('refresh_token', response.refreshToken);
    return response;
  }

  async logout(): Promise<void> {
    await AsyncStorage.removeItem('auth_token');
    await AsyncStorage.removeItem('refresh_token');
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
  async registerForRace(
    raceId: string,
    data: { categoryId: string; emergencyContact: Registration['emergencyContact'] },
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
    return this.request<RaceResult[]>('/results/my');
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
