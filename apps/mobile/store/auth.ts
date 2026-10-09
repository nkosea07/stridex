import { create } from 'zustand';
import AsyncStorage from '@react-native-async-storage/async-storage';
import * as SecureStore from 'expo-secure-store';
import { api } from '@/lib/api';
import type { AuthState } from '@/types';

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  token: null,
  refreshToken: null,
  isLoading: true,
  isAuthenticated: false,

  login: async (email: string, password: string) => {
    try {
      set({ isLoading: true });
      const response = await api.login(email, password);
      if (response.token && response.refreshToken) {
        await AsyncStorage.setItem('user', JSON.stringify(response.user));
        set({ user: response.user, token: response.token, refreshToken: response.refreshToken, isAuthenticated: true, isLoading: false });
        return { kind: 'authenticated' as const, user: response.user };
      }
      if (response.mfaRequired && response.mfaChallengeToken) {
        set({ isLoading: false });
        return {
          kind: 'mfa' as const,
          challengeToken: response.mfaChallengeToken,
          enrollmentRequired: Boolean(response.mfaEnrollmentRequired),
          user: response.user,
        };
      }
      if (response.requiresEmailVerification) throw new Error('Email verification required');
      throw new Error('Login did not return a valid session');
    } catch (error) {
      set({ isLoading: false });
      throw error;
    }
  },

  completeMfa: async (challengeToken, code, enrollment) => {
    const response = await api.completeMfa(challengeToken, code, enrollment);
    await AsyncStorage.setItem('user', JSON.stringify(response.user));
    set({ user: response.user, token: response.token!, refreshToken: response.refreshToken!, isAuthenticated: true, isLoading: false });
    return response;
  },

  register: async (userData) => {
    try {
      set({ isLoading: true });
      const response = await api.register(userData);

      if (response.token && response.refreshToken) {
        await AsyncStorage.setItem('user', JSON.stringify(response.user));
        set({
          user: response.user,
          token: response.token,
          refreshToken: response.refreshToken,
          isAuthenticated: true,
          isLoading: false,
        });
      } else {
        await api.logout();
        await AsyncStorage.removeItem('user');
        set({
          user: null,
          token: null,
          refreshToken: null,
          isAuthenticated: false,
          isLoading: false,
        });
      }

      return response;
    } catch (error) {
      set({ isLoading: false });
      throw error;
    }
  },

  logout: async () => {
    await api.logout();
    await AsyncStorage.removeItem('user');
    set({ user: null, token: null, refreshToken: null, isAuthenticated: false });
  },

  updateProfile: async (data) => {
    try {
      const user = await api.updateProfile(data);
      await AsyncStorage.setItem('user', JSON.stringify(user));
      set({ user });
    } catch (error) {
      throw error;
    }
  },
}));

// Initialize auth state from storage
export const initializeAuth = async () => {
  try {
    let token = await SecureStore.getItemAsync('auth_token');
    let refreshToken = await SecureStore.getItemAsync('refresh_token');
    if (!token) {
      token = await AsyncStorage.getItem('auth_token');
      if (token) {
        await SecureStore.setItemAsync('auth_token', token);
        await AsyncStorage.removeItem('auth_token');
      }
    }
    if (!refreshToken) {
      refreshToken = await AsyncStorage.getItem('refresh_token');
      if (refreshToken) {
        await SecureStore.setItemAsync('refresh_token', refreshToken);
        await AsyncStorage.removeItem('refresh_token');
      }
    }
    const userStr = await AsyncStorage.getItem('user');
    
    if (token) {
      try {
        const currentUser = await api.getCurrentUser();
        await AsyncStorage.setItem('user', JSON.stringify(currentUser));
        useAuthStore.setState({
          user: currentUser,
          token,
          refreshToken,
          isAuthenticated: true,
          isLoading: false,
        });
      } catch {
        await api.logout();
        await AsyncStorage.removeItem('user');
        useAuthStore.setState({
          user: null,
          token: null,
          refreshToken: null,
          isAuthenticated: false,
          isLoading: false,
        });
      }
    } else if (userStr) {
      useAuthStore.setState({ isLoading: false });
    } else {
      useAuthStore.setState({ isLoading: false });
    }
  } catch {
    useAuthStore.setState({ isLoading: false });
  }
};
