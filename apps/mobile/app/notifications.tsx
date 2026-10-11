import { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  FlatList,
  RefreshControl,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { api } from '@/lib/api';
import { registerForPushNotifications } from '@/lib/notifications';
import { useTheme } from '@/contexts/theme';
import { useAuthStore } from '@/store/auth';
import type { Notification } from '@/types';

const typeIcon: Record<Notification['type'], keyof typeof Ionicons.glyphMap> = {
  race_update: 'flag-outline',
  checkpoint_passed: 'location-outline',
  achievement: 'trophy-outline',
  reminder: 'alarm-outline',
  alert: 'warning-outline',
};

export default function NotificationsScreen() {
  const { colors } = useTheme();
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [loading, setLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [enablingPush, setEnablingPush] = useState(false);

  const load = useCallback(async () => {
    if (!isAuthenticated) return;
    setLoading(true);
    try {
      const items = await api.getNotifications();
      setNotifications(items);
    } catch (error) {
      Alert.alert('Unable to load notifications', error instanceof Error ? error.message : 'Please try again.');
    } finally {
      setLoading(false);
    }
  }, [isAuthenticated]);

  useEffect(() => {
    load();
  }, [load]);

  const onRefresh = async () => {
    setRefreshing(true);
    await load();
    setRefreshing(false);
  };

  const enablePush = async () => {
    setEnablingPush(true);
    try {
      const token = await registerForPushNotifications();
      Alert.alert(
        token ? 'Push enabled' : 'Push unavailable',
        token
          ? 'This device will receive race alerts.'
          : 'Notifications could not be enabled. Check device permissions and try again.',
      );
    } finally {
      setEnablingPush(false);
    }
  };

  const markRead = async (notification: Notification) => {
    if (notification.read) return;
    try {
      await api.markNotificationRead(notification.id);
      setNotifications((current) =>
        current.map((item) => (item.id === notification.id ? { ...item, read: true } : item)),
      );
    } catch {
      // Keep the local state unchanged; the next refresh reconciles.
    }
  };

  if (!isAuthenticated) {
    return (
      <View style={[styles.empty, { backgroundColor: colors.background }]}>
        <Text style={[styles.emptyText, { color: colors.subText }]}>Sign in to view notifications.</Text>
      </View>
    );
  }

  return (
    <View style={[styles.container, { backgroundColor: colors.background }]}>
      <TouchableOpacity
        style={[styles.pushButton, { backgroundColor: colors.primary }]}
        onPress={enablePush}
        disabled={enablingPush}
      >
        {enablingPush ? (
          <ActivityIndicator color="#FFFFFF" />
        ) : (
          <>
            <Ionicons name="notifications" size={18} color="#FFFFFF" />
            <Text style={styles.pushButtonText}>Enable push notifications</Text>
          </>
        )}
      </TouchableOpacity>

      {loading && notifications.length === 0 ? (
        <View style={styles.empty}>
          <ActivityIndicator color={colors.primary} />
        </View>
      ) : (
        <FlatList
          data={notifications}
          keyExtractor={(item) => item.id}
          refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
          contentContainerStyle={notifications.length === 0 ? styles.empty : undefined}
          ListEmptyComponent={
            <Text style={[styles.emptyText, { color: colors.subText }]}>No notifications yet.</Text>
          }
          renderItem={({ item }) => (
            <TouchableOpacity
              onPress={() => markRead(item)}
              style={[
                styles.row,
                {
                  backgroundColor: colors.cardBackground,
                  borderColor: colors.border,
                  opacity: item.read ? 0.6 : 1,
                },
              ]}
            >
              <Ionicons name={typeIcon[item.type] ?? 'notifications-outline'} size={22} color={colors.primary} />
              <View style={styles.rowBody}>
                <Text style={[styles.rowTitle, { color: colors.text }]}>{item.title}</Text>
                <Text style={[styles.rowMessage, { color: colors.subText }]}>{item.message}</Text>
                <Text style={[styles.rowTimestamp, { color: colors.subText }]}>
                  {new Date(item.timestamp).toLocaleString()}
                </Text>
              </View>
              {!item.read && <View style={[styles.unreadDot, { backgroundColor: colors.cta }]} />}
            </TouchableOpacity>
          )}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 16 },
  pushButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    paddingVertical: 12,
    borderRadius: 12,
    marginBottom: 16,
  },
  pushButtonText: { color: '#FFFFFF', fontWeight: '700', fontSize: 15 },
  row: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 12,
    borderWidth: 1,
    borderRadius: 12,
    padding: 14,
    marginBottom: 10,
  },
  rowBody: { flex: 1, gap: 2 },
  rowTitle: { fontSize: 16, fontWeight: '600' },
  rowMessage: { fontSize: 14 },
  rowTimestamp: { fontSize: 12, marginTop: 4 },
  unreadDot: { width: 10, height: 10, borderRadius: 5, marginTop: 6 },
  empty: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
  emptyText: { fontSize: 15, textAlign: 'center' },
});
