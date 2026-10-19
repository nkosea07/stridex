import { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { router } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { api } from '@/lib/api';
import { useAuthStore } from '@/store/auth';
import { useTheme } from '@/contexts/theme';
import { AuthenticatedGate } from '@/components/CapabilityGate';
import type { DeletionStatus } from '@/types';

export default function AccountDeletionScreen() {
  return (
    <AuthenticatedGate>
      <AccountDeletionScreenContent />
    </AuthenticatedGate>
  );
}

function AccountDeletionScreenContent() {
  const { colors } = useTheme();
  const logout = useAuthStore((state) => state.logout);
  const [status, setStatus] = useState<DeletionStatus | null>(null);
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      setStatus(await api.deletionStatus());
    } catch {
      setStatus({ deletionRequested: false });
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const handleDelete = async () => {
    if (confirmation.trim().toUpperCase() !== 'DELETE') {
      Alert.alert('Confirmation required', 'Type DELETE to confirm.');
      return;
    }
    setBusy(true);
    try {
      await api.deleteAccount(password, confirmation.trim());
      try {
        await logout();
      } catch (error) {
        console.warn('Account deletion completed but local logout cleanup reported an error:', error);
      }
      Alert.alert(
        'Account scheduled for deletion',
        'Your account is deactivated and your data will be erased after the grace period. You will now be signed out.',
        [{ text: 'OK', onPress: () => router.replace('/(auth)/login') }],
      );
    } catch (error) {
      Alert.alert('Unable to delete account', error instanceof Error ? error.message : 'Please try again.');
    } finally {
      setBusy(false);
    }
  };

  const handleCancel = async () => {
    setBusy(true);
    try {
      await api.cancelDeletion();
      await load();
      Alert.alert('Deletion cancelled', 'Your account has been reactivated.');
    } catch (error) {
      Alert.alert('Unable to cancel', error instanceof Error ? error.message : 'Please try again.');
    } finally {
      setBusy(false);
    }
  };

  if (loading) {
    return (
      <View style={[styles.center, { backgroundColor: colors.background }]}>
        <ActivityIndicator color={colors.primary} />
      </View>
    );
  }

  return (
    <ScrollView style={[styles.container, { backgroundColor: colors.background }]} contentContainerStyle={styles.content}>
      {status?.deletionRequested ? (
        <View style={[styles.card, { backgroundColor: colors.cardBackground, borderColor: colors.border }]}>
          <Text style={[styles.title, { color: colors.text }]}>Deletion scheduled</Text>
          <Text style={[styles.body, { color: colors.subText }]}>
            Your account is deactivated and scheduled for erasure
            {status.scheduledFor ? ` on ${new Date(status.scheduledFor).toLocaleDateString()}` : ''}. You can still
            cancel before then.
          </Text>
          <TouchableOpacity
            style={[styles.button, { backgroundColor: colors.primary }]}
            onPress={() => void handleCancel()}
            disabled={busy}
          >
            {busy ? <ActivityIndicator color="#FFFFFF" /> : <Text style={styles.buttonText}>Cancel deletion</Text>}
          </TouchableOpacity>
        </View>
      ) : (
        <View style={[styles.card, { backgroundColor: colors.cardBackground, borderColor: colors.border }]}>
          <View style={styles.warningRow}>
            <Ionicons name="warning-outline" size={22} color={colors.danger} />
            <Text style={[styles.title, { color: colors.danger }]}>Delete account</Text>
          </View>
          <Text style={[styles.body, { color: colors.subText }]}>
            This deactivates your account immediately and erases your personal data after a grace period. Download
            anything you need first. This cannot be undone once the grace period ends.
          </Text>

          <TextInput
            style={[styles.input, { borderColor: colors.border, color: colors.text }]}
            placeholder="Your password"
            placeholderTextColor={colors.subText}
            secureTextEntry
            value={password}
            onChangeText={setPassword}
            autoCapitalize="none"
          />
          <TextInput
            style={[styles.input, { borderColor: colors.border, color: colors.text }]}
            placeholder="Type DELETE to confirm"
            placeholderTextColor={colors.subText}
            value={confirmation}
            onChangeText={setConfirmation}
            autoCapitalize="characters"
          />

          <TouchableOpacity
            style={[styles.button, { backgroundColor: colors.danger }]}
            onPress={() => void handleDelete()}
            disabled={busy}
          >
            {busy ? <ActivityIndicator color="#FFFFFF" /> : <Text style={styles.buttonText}>Delete my account</Text>}
          </TouchableOpacity>
        </View>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: 20 },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  card: { borderWidth: 1, borderRadius: 16, padding: 18, gap: 12 },
  warningRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  title: { fontSize: 18, fontWeight: '800' },
  body: { fontSize: 14, lineHeight: 21 },
  input: { borderWidth: 1, borderRadius: 10, paddingHorizontal: 14, paddingVertical: 12, fontSize: 16 },
  button: { alignItems: 'center', paddingVertical: 14, borderRadius: 12, marginTop: 4 },
  buttonText: { color: '#FFFFFF', fontWeight: '700', fontSize: 15 },
});
