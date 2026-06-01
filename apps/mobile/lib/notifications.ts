import { Platform } from 'react-native';
import * as Notifications from 'expo-notifications';

export const TRACKING_NOTIFICATION_CHANNEL_ID = 'race-tracking';

export const configureNotificationChannels = async () => {
  if (Platform.OS !== 'android') return;

  await Notifications.setNotificationChannelAsync(TRACKING_NOTIFICATION_CHANNEL_ID, {
    name: 'Race tracking',
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
