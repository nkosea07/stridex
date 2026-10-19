import { create } from 'zustand';
import AsyncStorage from '@react-native-async-storage/async-storage';
import * as SecureStore from 'expo-secure-store';
import { api, setSessionInvalidatedHandler } from '@/lib/api';
import { unregisterCurrentPushDevice } from '@/lib/notifications';
import { createSessionCleanup } from '@/lib/session-cleanup';
import { useTrackingStore } from '@/store/tracking';
import { getUserCapabilities, normalizeUser } from '@/lib/capabilities';
import type { AuthState } from '@/types';

const cleanupSessionArtifacts = createSessionCleanup({
  unregisterPushDevice: unregisterCurrentPushDevice,
  clearTracking: () => useTrackingStore.getState().clearTrackingData(),
});

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
        const user = normalizeUser(response.user);
        await AsyncStorage.setItem('user', JSON.stringify(user));
        set({ user, token: response.token, refreshToken: response.refreshToken, isAuthenticated: true, isLoading: false });
        return { kind: 'authenticated' as const, user };
      }
      if (response.requiresEmailVerification) throw new Error('Email verification required');
      throw new Error('Login did not return a valid session');
    } catch (error) {
      set({ isLoading: false });
      throw error;
    }
  },

  register: async (userData) => {
    try {
      set({ isLoading: true });
      const response = await api.register(userData);

      if (response.token && response.refreshToken) {
        const user = normalizeUser(response.user);
        await AsyncStorage.setItem('user', JSON.stringify(user));
        set({
          user,
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
    try {
      await cleanupSessionArtifacts();
    } finally {
      try {
        await api.logout();
      } finally {
        try {
          await AsyncStorage.removeItem('user');
        } finally {
          clearAuthState();
        }
      }
    }
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

  updateCapabilities: async (data) => {
    const currentUser = useAuthStore.getState().user;
    const capabilities = getUserCapabilities(currentUser);
    if (!currentUser || !capabilities.includes(data.preferredCapability) || capabilities.length === 0) {
      throw new Error('The selected experience is not available for this account.');
    }

    const user = await api.updateCapabilities({ capabilities, preferredCapability: data.preferredCapability });
    await AsyncStorage.setItem('user', JSON.stringify(user));
    set({ user });
  },
}));

export const clearAuthState = (): void => {
  useAuthStore.setState({
    user: null,
    token: null,
    refreshToken: null,
    isAuthenticated: false,
    isLoading: false,
  });
};

setSessionInvalidatedHandler(async () => {
  try {
    await cleanupSessionArtifacts();
  } finally {
    clearAuthState();
  }
});

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
        try {
          await useAuthStore.getState().logout();
        } catch {
          clearAuthState();
        }
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
