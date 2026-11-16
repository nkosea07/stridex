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
import { useTranslation } from 'react-i18next';
import { api, ApiRequestError } from '@/lib/api';
import { formatDate } from '@/lib/format';
import { asList } from '@/lib/legal';
import {
  DELETION_CONFIRMATION_TOKEN,
  DELETION_GRACE_DAYS,
  deletionErrorKey,
  isDeletionBlocker,
  knownBlockers,
} from '@/lib/accountDeletion';
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
  const { t } = useTranslation();
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
      setStatus({ deletionRequested: false, blockers: [] });
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const blockers = status?.deletionRequested ? [] : knownBlockers(status?.blockers);
  const consequences = asList<string>(t('account:deletion.consequences', {
    returnObjects: true,
    days: DELETION_GRACE_DAYS,
  }));

  const showError = (key: string) => {
    Alert.alert(t('account:deletion.errorTitle'), t(key, { token: DELETION_CONFIRMATION_TOKEN }));
  };

  const handleDelete = async () => {
    if (!password) {
      showError('account:deletion.errors.ACCOUNT_DELETION_PASSWORD_REQUIRED');
      return;
    }
    if (confirmation.trim().toUpperCase() !== DELETION_CONFIRMATION_TOKEN) {
      showError('account:deletion.errors.ACCOUNT_DELETION_CONFIRMATION_REQUIRED');
      return;
    }
    setBusy(true);
    try {
      const scheduled = await api.deleteAccount(password, confirmation.trim());
      setPassword('');
      try {
        await logout();
      } catch (error) {
        console.warn('Account deletion completed but local logout cleanup reported an error:', error);
      }
      Alert.alert(
        t('account:deletion.successTitle'),
        t('account:deletion.successBody', {
          date: scheduled.scheduledFor ? formatDate(scheduled.scheduledFor) : '',
        }),
        [{ text: t('account:deletion.ok'), onPress: () => router.replace('/(auth)/login') }],
      );
    } catch (error) {
      showError(deletionErrorKey(error));
      const code = error instanceof ApiRequestError ? error.code : undefined;
      if (isDeletionBlocker(code)) {
        setStatus((current) => ({
          deletionRequested: current?.deletionRequested ?? false,
          requestedAt: current?.requestedAt ?? null,
          scheduledFor: current?.scheduledFor ?? null,
          blockers: Array.from(new Set([...(current?.blockers ?? []), code])),
        }));
      }
    } finally {
      setBusy(false);
    }
  };

  const handleCancel = async () => {
    setBusy(true);
    try {
      await api.cancelDeletion();
      await load();
      Alert.alert(t('account:deletion.cancelledTitle'), t('account:deletion.cancelledBody'));
    } catch {
      Alert.alert(t('account:deletion.errorTitle'), t('account:deletion.cancelFailed'));
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
          <Text accessibilityRole="header" style={[styles.title, { color: colors.text }]}>
            {t('account:deletion.scheduledTitle')}
          </Text>
          <Text style={[styles.body, { color: colors.subText }]}>
            {status.scheduledFor
              ? t('account:deletion.scheduledBody', { date: formatDate(status.scheduledFor) })
              : t('account:deletion.scheduledBodyNoDate')}
          </Text>
          <TouchableOpacity
            accessibilityRole="button"
            style={[styles.button, { backgroundColor: colors.primary }]}
            onPress={() => void handleCancel()}
            disabled={busy}
          >
            {busy ? <ActivityIndicator color="#FFFFFF" /> : (
              <Text style={styles.buttonText}>{t('account:deletion.cancel')}</Text>
            )}
          </TouchableOpacity>
        </View>
      ) : (
        <View style={[styles.card, { backgroundColor: colors.cardBackground, borderColor: colors.border }]}>
          <View style={styles.warningRow}>
            <Ionicons name="warning-outline" size={22} color={colors.danger} />
            <Text accessibilityRole="header" style={[styles.title, { color: colors.danger }]}>
              {t('account:deletion.title')}
            </Text>
          </View>
          <Text style={[styles.body, { color: colors.subText }]}>
            {t('account:deletion.description', { days: DELETION_GRACE_DAYS })}
          </Text>

          {blockers.length > 0 ? (
            <View
              accessibilityLiveRegion="polite"
              style={[styles.blocker, { borderColor: colors.danger }]}
            >
              <Text style={[styles.subtitle, { color: colors.danger }]}>{t('account:deletion.blockedTitle')}</Text>
              {blockers.map((code) => (
                <Text key={code} style={[styles.body, { color: colors.text }]}>
                  {t(`account:deletion.blockers.${code}`)}
                </Text>
              ))}
            </View>
          ) : (
            <>
              <Text style={[styles.subtitle, { color: colors.text }]}>{t('account:deletion.consequencesTitle')}</Text>
              {consequences.map((item) => (
                <View key={item} style={styles.itemRow}>
                  <Text style={[styles.body, { color: colors.subText }]}>{'\u2022'}</Text>
                  <Text style={[styles.body, styles.itemText, { color: colors.subText }]}>{item}</Text>
                </View>
              ))}
              <Text style={[styles.body, { color: colors.subText }]}>{t('account:deletion.exportHint')}</Text>
              <Text style={[styles.body, { color: colors.subText }]}>{t('account:deletion.recoveryHint')}</Text>

              <TextInput
                accessibilityLabel={t('account:deletion.passwordLabel')}
                style={[styles.input, { borderColor: colors.border, color: colors.text }]}
                placeholder={t('account:deletion.passwordLabel')}
                placeholderTextColor={colors.subText}
                secureTextEntry
                value={password}
                onChangeText={setPassword}
                autoCapitalize="none"
                autoComplete="current-password"
                textContentType="password"
              />
              <TextInput
                accessibilityLabel={t('account:deletion.confirmLabel', { token: DELETION_CONFIRMATION_TOKEN })}
                style={[styles.input, { borderColor: colors.border, color: colors.text }]}
                placeholder={t('account:deletion.confirmLabel', { token: DELETION_CONFIRMATION_TOKEN })}
                placeholderTextColor={colors.subText}
                value={confirmation}
                onChangeText={setConfirmation}
                autoCapitalize="characters"
                autoCorrect={false}
              />

              <TouchableOpacity
                accessibilityRole="button"
                accessibilityState={{ disabled: busy }}
                style={[styles.button, { backgroundColor: colors.danger }]}
                onPress={() => void handleDelete()}
                disabled={busy}
              >
                {busy ? <ActivityIndicator color="#FFFFFF" /> : (
                  <Text style={styles.buttonText}>{t('account:deletion.submit')}</Text>
                )}
              </TouchableOpacity>
            </>
          )}
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
  subtitle: { fontSize: 15, fontWeight: '700' },
  blocker: { borderWidth: 1, borderRadius: 12, padding: 12, gap: 6 },
  itemRow: { flexDirection: 'row', gap: 8, paddingRight: 8 },
  itemText: { flex: 1 },
  body: { fontSize: 14, lineHeight: 21 },
  input: { borderWidth: 1, borderRadius: 10, paddingHorizontal: 14, paddingVertical: 12, fontSize: 16 },
  button: { alignItems: 'center', paddingVertical: 14, borderRadius: 12, marginTop: 4 },
  buttonText: { color: '#FFFFFF', fontWeight: '700', fontSize: 15 },
});
