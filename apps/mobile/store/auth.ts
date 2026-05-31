import { create } from 'zustand';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { api } from '@/lib/api';
import type { User, AuthState } from '@/types';

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  token: null,
  refreshToken: null,
  isLoading: true,
  isAuthenticated: false,

  login: async (email: string, password: string) => {
    try {
      set({ isLoading: true });
      const { user, token, refreshToken } = await api.login(email, password);
      await AsyncStorage.setItem('auth_token', token);
      await AsyncStorage.setItem('refresh_token', refreshToken);
      await AsyncStorage.setItem('user', JSON.stringify(user));
      set({ user, token, refreshToken, isAuthenticated: true, isLoading: false });
    } catch (error) {
      set({ isLoading: false });
      throw error;
    }
  },

  register: async (userData) => {
    try {
      set({ isLoading: true });
      const { user, token, refreshToken } = await api.register(userData);
      await AsyncStorage.setItem('auth_token', token);
      await AsyncStorage.setItem('refresh_token', refreshToken);
      await AsyncStorage.setItem('user', JSON.stringify(user));
      set({ user, token, refreshToken, isAuthenticated: true, isLoading: false });
    } catch (error) {
      set({ isLoading: false });
      throw error;
    }
  },

  logout: async () => {
    await AsyncStorage.removeItem('auth_token');
    await AsyncStorage.removeItem('refresh_token');
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
    const token = await AsyncStorage.getItem('auth_token');
    const refreshToken = await AsyncStorage.getItem('refresh_token');
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
        await AsyncStorage.removeItem('auth_token');
        await AsyncStorage.removeItem('refresh_token');
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
