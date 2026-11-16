import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Alert, ScrollView, Share, StyleSheet, Switch, Text, TouchableOpacity, View } from 'react-native';
import { router } from 'expo-router';
import { useTranslation } from 'react-i18next';
import { AuthenticatedGate } from '@/components/CapabilityGate';
import { useTheme } from '@/contexts/theme';
import { api } from '@/lib/api';
import { formatDate } from '@/lib/format';
import type { ConsentState, NotificationPreferences } from '@/types';

type Channel = 'pushEnabled' | 'emailEnabled' | 'whatsappEnabled';
const CHANNELS: Channel[] = ['pushEnabled', 'emailEnabled', 'whatsappEnabled'];

export default function SettingsScreen() {
  return (
    <AuthenticatedGate>
      <SettingsContent />
    </AuthenticatedGate>
  );
}

function SettingsContent() {
  const { t } = useTranslation();
  const { colors } = useTheme();
  const [preferences, setPreferences] = useState<NotificationPreferences | null>(null);
  const [consent, setConsent] = useState<ConsentState | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);
  const [loadFailed, setLoadFailed] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadFailed(false);
    try {
      const [prefs, consentState] = await Promise.all([api.getNotificationPreferences(), api.getConsent()]);
      setPreferences(prefs);
      setConsent(consentState);
    } catch {
      setLoadFailed(true);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const toggleChannel = async (channel: Channel, value: boolean) => {
    if (!preferences) return;
    const previous = preferences;
    const next = { ...preferences, [channel]: value };
    setPreferences(next);
    setSaving(channel);
    try {
      setPreferences(await api.updateNotificationPreferences(next));
    } catch {
      setPreferences(previous);
      Alert.alert(t('mobile:settings.saveFailedTitle'), t('mobile:settings.saveFailed'));
    } finally {
      setSaving(null);
    }
  };

  const toggleMarketing = async (value: boolean) => {
    if (!consent) return;
    const previous = consent;
    setConsent({ ...consent, marketingConsent: value });
    setSaving('marketing');
    try {
      setConsent(await api.updateMarketingConsent(value));
    } catch {
      setConsent(previous);
      Alert.alert(t('mobile:settings.saveFailedTitle'), t('mobile:settings.saveFailed'));
    } finally {
      setSaving(null);
    }
  };

  const exportData = async () => {
    setExporting(true);
    try {
      const json = await api.exportMyData();
      await Share.share({ title: t('mobile:settings.exportShareTitle'), message: json });
    } catch {
      Alert.alert(t('mobile:settings.exportTitle'), t('mobile:settings.exportFailed'));
    } finally {
      setExporting(false);
    }
  };

  if (loading) {
    return (
      <View style={[styles.center, { backgroundColor: colors.background }]}>
        <ActivityIndicator color={colors.primary} accessibilityLabel={t('mobile:settings.loading')} />
      </View>
    );
  }

  return (
    <ScrollView style={{ backgroundColor: colors.background }} contentContainerStyle={styles.container}>
      {loadFailed ? (
        <View style={[styles.card, { backgroundColor: colors.cardBackground }]}>
          <Text style={[styles.body, { color: colors.text }]} accessibilityRole="alert">{t('mobile:settings.loadFailed')}</Text>
          <TouchableOpacity onPress={() => void load()} accessibilityRole="button">
            <Text style={[styles.link, { color: colors.primary }]}>{t('mobile:settings.retry')}</Text>
          </TouchableOpacity>
        </View>
      ) : null}

      {preferences ? (
        <View style={[styles.card, { backgroundColor: colors.cardBackground }]}>
          <Text style={[styles.heading, { color: colors.text }]} accessibilityRole="header">{t('mobile:settings.notificationsTitle')}</Text>
          <Text style={[styles.body, { color: colors.subText }]}>{t('mobile:settings.notificationsHelp')}</Text>
          {CHANNELS.map((channel) => (
            <View key={channel} style={styles.row}>
              <View style={styles.rowText}>
                <Text style={[styles.label, { color: colors.text }]}>{t(`mobile:settings.channel.${channel}`)}</Text>
                <Text style={[styles.hint, { color: colors.subText }]}>{t(`mobile:settings.channelHint.${channel}`)}</Text>
              </View>
              <Switch
                value={preferences[channel]}
                disabled={saving !== null}
                onValueChange={(value) => void toggleChannel(channel, value)}
                accessibilityLabel={t(`mobile:settings.channel.${channel}`)}
              />
            </View>
          ))}
          <Text style={[styles.hint, { color: colors.subText }]}>{t('mobile:settings.safetyNote')}</Text>
        </View>
      ) : null}

      {consent ? (
        <View style={[styles.card, { backgroundColor: colors.cardBackground }]}>
          <Text style={[styles.heading, { color: colors.text }]} accessibilityRole="header">{t('mobile:settings.privacyTitle')}</Text>
          <View style={styles.row}>
            <View style={styles.rowText}>
              <Text style={[styles.label, { color: colors.text }]}>{t('mobile:settings.marketing')}</Text>
              <Text style={[styles.hint, { color: colors.subText }]}>{t('mobile:settings.marketingHint')}</Text>
            </View>
            <Switch value={consent.marketingConsent} disabled={saving !== null}
              onValueChange={(value) => void toggleMarketing(value)} accessibilityLabel={t('mobile:settings.marketing')} />
          </View>
          {consent.dataProcessingConsentAt ? (
            <Text style={[styles.hint, { color: colors.subText }]}>
              {t('mobile:settings.processingConsent', { date: formatDate(consent.dataProcessingConsentAt) })}
            </Text>
          ) : null}
          <TouchableOpacity style={[styles.button, { borderColor: colors.primary }, exporting && styles.disabled]}
            disabled={exporting} onPress={() => void exportData()} accessibilityRole="button"
            accessibilityState={{ busy: exporting, disabled: exporting }}>
            <Text style={[styles.buttonText, { color: colors.primary }]}>
              {exporting ? t('mobile:settings.exporting') : t('mobile:settings.export')}
            </Text>
          </TouchableOpacity>
          <Text style={[styles.hint, { color: colors.subText }]}>{t('mobile:settings.exportHint')}</Text>
          <TouchableOpacity onPress={() => router.push('/privacy')} accessibilityRole="link">
            <Text style={[styles.link, { color: colors.primary }]}>{t('legal:common.privacyPolicy')}</Text>
          </TouchableOpacity>
          <TouchableOpacity onPress={() => router.push('/account-deletion')} accessibilityRole="link">
            <Text style={[styles.link, { color: colors.primary }]}>{t('account:deletion.title')}</Text>
          </TouchableOpacity>
        </View>
      ) : null}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { padding: 16, gap: 16 },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  card: { borderRadius: 16, padding: 16, gap: 12 },
  heading: { fontSize: 18, fontWeight: '700' },
  body: { fontSize: 14, lineHeight: 20 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  rowText: { flex: 1 },
  label: { fontSize: 15, fontWeight: '600' },
  hint: { fontSize: 13, lineHeight: 18 },
  link: { fontSize: 15, fontWeight: '600', paddingVertical: 4 },
  button: { borderWidth: 1, borderRadius: 12, padding: 14, alignItems: 'center' },
  buttonText: { fontWeight: '700' },
  disabled: { opacity: 0.5 },
});
