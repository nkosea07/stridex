import { Platform } from 'react-native';
import * as Notifications from 'expo-notifications';
import { api } from './api';

export const TRACKING_NOTIFICATION_CHANNEL_ID = 'race-tracking';
export const ALERTS_NOTIFICATION_CHANNEL_ID = 'race-alerts';

// Foreground presentation policy: show alerts while the app is open.
Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowBanner: true,
    shouldShowList: true,
    shouldPlaySound: false,
    shouldSetBadge: false,
  }),
});

export const configureNotificationChannels = async () => {
  if (Platform.OS !== 'android') return;

  await Notifications.setNotificationChannelAsync(TRACKING_NOTIFICATION_CHANNEL_ID, {
    name: 'Race tracking',
    importance: Notifications.AndroidImportance.HIGH,
    vibrationPattern: [0, 250, 250, 250],
    lightColor: '#0F766E',
  });

  await Notifications.setNotificationChannelAsync(ALERTS_NOTIFICATION_CHANNEL_ID, {
    name: 'Race alerts',
    importance: Notifications.AndroidImportance.HIGH,
    vibrationPattern: [0, 250, 250, 250],
    lightColor: '#0F766E',
  });
};

export const requestLocalNotificationPermissions = async () => {
  const current = await Notifications.getPermissionsAsync();
  if (current.granted) return true;

  const requested = await Notifications.requestPermissionsAsync();
  return requested.granted;
};

/**
 * Registers this device's native push token (FCM/APNs) with the backend so it can receive
 * remote notifications. Returns the token, or null when push is unavailable (permission
 * denied, running in a simulator, or FCM credentials not configured in the build).
 */
export const registerForPushNotifications = async (): Promise<string | null> => {
  await configureNotificationChannels();

  const granted = await requestLocalNotificationPermissions();
  if (!granted) return null;

  try {
    const tokenResult = await Notifications.getDevicePushTokenAsync();
    const token = typeof tokenResult?.data === 'string' ? tokenResult.data : null;
    if (!token) return null;
    await api.registerDevice(token, Platform.OS);
    return token;
  } catch (error) {
    console.warn('Push notification registration unavailable:', error);
    return null;
  }
};

export const unregisterPushDevice = async (token: string): Promise<void> => {
  try {
    await api.unregisterDevice(token);
  } catch (error) {
    console.warn('Failed to unregister push device:', error);
  }
};

export const addNotificationResponseListener = (
  handler: (response: Notifications.NotificationResponse) => void
) => Notifications.addNotificationResponseReceivedListener(handler);

export const addNotificationReceivedListener = (
  handler: (notification: Notifications.Notification) => void
) => Notifications.addNotificationReceivedListener(handler);
