import { useState } from 'react';
import { ActivityIndicator, Alert, ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { router } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { useTranslation } from 'react-i18next';
import { useTheme } from '@/contexts/theme';
import { useAuthStore } from '@/store/auth';
import { ApiRequestError } from '@/lib/api';
import { LEGAL_VERSION_MISMATCH_CODES } from '@/lib/legal';

export default function LegalAcceptanceScreen() {
  const { colors } = useTheme();
  const { t } = useTranslation();
  const { user, acceptLegalDocuments, logout } = useAuthStore();
  const [agreed, setAgreed] = useState(false);
  const [saving, setSaving] = useState(false);
  const previouslyAccepted = Boolean(user?.termsVersion || user?.privacyPolicyVersion);

  const accept = async () => {
    if (!agreed || saving) return;
    setSaving(true);
    try {
      await acceptLegalDocuments();
      router.replace('/');
    } catch (error) {
      const outdated = error instanceof ApiRequestError && error.code !== undefined
        && LEGAL_VERSION_MISMATCH_CODES.has(error.code);
      Alert.alert(t('legal:acceptance.title'), t(outdated ? 'legal:acceptance.outdated' : 'legal:acceptance.failed'));
    } finally {
      setSaving(false);
    }
  };

  const signOut = async () => {
    await logout();
    router.replace('/(auth)/login');
  };

  return (
    <ScrollView style={[styles.container, { backgroundColor: colors.background }]} contentContainerStyle={styles.content}>
      <Ionicons name="document-text-outline" size={40} color={colors.primary} />
      <Text accessibilityRole="header" style={[styles.title, { color: colors.text }]}>{t('legal:acceptance.title')}</Text>
      <Text style={[styles.body, { color: colors.subText }]}>
        {previouslyAccepted ? t('legal:acceptance.updatedBody') : t('legal:acceptance.body')}
      </Text>

      <View style={[styles.links, { borderColor: colors.border, backgroundColor: colors.cardBackground }]}>
        <TouchableOpacity accessibilityRole="link" style={styles.linkRow} onPress={() => router.push('/terms')}>
          <Text style={[styles.link, { color: colors.primary }]}>{t('legal:common.terms')}</Text>
          <Ionicons name="chevron-forward" size={18} color={colors.subText} />
        </TouchableOpacity>
        <TouchableOpacity accessibilityRole="link" style={styles.linkRow} onPress={() => router.push('/privacy')}>
          <Text style={[styles.link, { color: colors.primary }]}>{t('legal:common.privacyPolicy')}</Text>
          <Ionicons name="chevron-forward" size={18} color={colors.subText} />
        </TouchableOpacity>
      </View>

      <TouchableOpacity
        style={styles.checkboxRow}
        onPress={() => setAgreed((value) => !value)}
        accessibilityRole="checkbox"
        accessibilityState={{ checked: agreed }}
      >
        <Ionicons name={agreed ? 'checkbox' : 'square-outline'} size={22} color={agreed ? colors.success : colors.subText} />
        <Text style={[styles.checkboxText, { color: colors.text }]}>{t('legal:acceptance.checkbox')}</Text>
      </TouchableOpacity>

      <TouchableOpacity
        accessibilityRole="button"
        accessibilityState={{ disabled: !agreed || saving }}
        disabled={!agreed || saving}
        onPress={() => void accept()}
        style={[styles.primaryButton, { backgroundColor: colors.primary }, (!agreed || saving) && styles.disabled]}
      >
        {saving ? <ActivityIndicator color="#FFFFFF" /> : (
          <Text style={styles.primaryButtonText}>{t('legal:acceptance.accept')}</Text>
        )}
      </TouchableOpacity>
      <TouchableOpacity accessibilityRole="button" onPress={() => void signOut()} style={styles.secondaryButton} disabled={saving}>
        <Text style={[styles.secondaryButtonText, { color: colors.subText }]}>{t('legal:acceptance.signOut')}</Text>
      </TouchableOpacity>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  content: { padding: 24, paddingTop: 48, paddingBottom: 48 },
  title: { fontSize: 22, fontWeight: '800', marginTop: 12 },
  body: { fontSize: 15, lineHeight: 22, marginTop: 8 },
  links: { marginTop: 20, borderWidth: 1, borderRadius: 12 },
  linkRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', padding: 14 },
  link: { fontSize: 15, fontWeight: '600' },
  checkboxRow: { flexDirection: 'row', alignItems: 'flex-start', gap: 10, marginTop: 20 },
  checkboxText: { flex: 1, fontSize: 14, lineHeight: 20 },
  primaryButton: { marginTop: 24, borderRadius: 12, paddingVertical: 14, alignItems: 'center' },
  primaryButtonText: { color: '#FFFFFF', fontSize: 16, fontWeight: '700' },
  disabled: { opacity: 0.5 },
  secondaryButton: { marginTop: 12, paddingVertical: 12, alignItems: 'center' },
  secondaryButtonText: { fontSize: 15, fontWeight: '600' },
});
