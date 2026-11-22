import { useState } from 'react';
import { ActivityIndicator, KeyboardAvoidingView, Platform, ScrollView, Text, TextInput, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { router, useLocalSearchParams } from 'expo-router';
import { useTranslation } from 'react-i18next';
import { api, ApiRequestError } from '@/lib/api';
import { PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH, readLinkToken, validateNewPassword } from '@/lib/passwordRules';
import { useAuthStore } from '@/store/auth';
import { authLinkStyles as styles } from '@/components/authLinkStyles';

export default function ResetPasswordScreen() {
  const { t } = useTranslation();
  const params = useLocalSearchParams<{ token?: string | string[] }>();
  const [token] = useState(() => readLinkToken(params.token));
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [linkInvalid, setLinkInvalid] = useState(!token);
  const [done, setDone] = useState(false);

  const submit = async () => {
    if (!token) return;
    const problem = validateNewPassword(password, confirmation);
    if (problem) {
      setError(t(`mobile:authLinks.${problem}`, { min: PASSWORD_MIN_LENGTH, max: PASSWORD_MAX_LENGTH }));
      return;
    }
    setError(null);
    setSaving(true);
    try {
      await api.resetPassword(token, password);
      setPassword('');
      setConfirmation('');
      setDone(true);
      if (useAuthStore.getState().isAuthenticated) {
        await useAuthStore.getState().logout().catch(() => undefined);
      }
    } catch (caught) {
      if (caught instanceof ApiRequestError && caught.isNetworkError) {
        setError(t('mobile:forgotPassword.network'));
      } else if (caught instanceof ApiRequestError && caught.status === 429) {
        setError(t('mobile:forgotPassword.tooMany'));
      } else {
        setLinkInvalid(true);
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <KeyboardAvoidingView style={styles.container} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <Text style={styles.title} accessibilityRole="header">{t('mobile:authLinks.resetTitle')}</Text>
        {done ? (
          <>
            <View style={styles.status} accessibilityLiveRegion="polite">
              <Ionicons name="checkmark-circle" size={22} color="#0F766E" />
              <Text style={[styles.body, { flex: 1 }]}>{t('mobile:authLinks.resetDone')}</Text>
            </View>
            <TouchableOpacity style={styles.button} onPress={() => router.replace('/(auth)/login')} accessibilityRole="button">
              <Text style={styles.buttonText}>{t('mobile:authLinks.signIn')}</Text>
            </TouchableOpacity>
          </>
        ) : linkInvalid ? (
          <>
            <Text style={styles.error} accessibilityLiveRegion="polite">{t('mobile:authLinks.resetInvalid')}</Text>
            <TouchableOpacity style={styles.button} onPress={() => router.replace('/(auth)/forgot-password')} accessibilityRole="button">
              <Text style={styles.buttonText}>{t('mobile:authLinks.requestNewLink')}</Text>
            </TouchableOpacity>
          </>
        ) : (
          <>
            <Text style={styles.body}>{t('mobile:authLinks.resetIntro', { min: PASSWORD_MIN_LENGTH })}</Text>
            <Text style={styles.label} nativeID="new-password-label">{t('mobile:authLinks.newPassword')}</Text>
            <TextInput
              style={styles.input}
              value={password}
              onChangeText={setPassword}
              secureTextEntry
              autoCapitalize="none"
              autoComplete="new-password"
              textContentType="newPassword"
              maxLength={PASSWORD_MAX_LENGTH}
              accessibilityLabel={t('mobile:authLinks.newPassword')}
              accessibilityLabelledBy="new-password-label"
            />
            <Text style={styles.label} nativeID="confirm-password-label">{t('mobile:authLinks.confirmPassword')}</Text>
            <TextInput
              style={styles.input}
              value={confirmation}
              onChangeText={setConfirmation}
              secureTextEntry
              autoCapitalize="none"
              autoComplete="new-password"
              textContentType="newPassword"
              maxLength={PASSWORD_MAX_LENGTH}
              accessibilityLabel={t('mobile:authLinks.confirmPassword')}
              accessibilityLabelledBy="confirm-password-label"
              onSubmitEditing={() => void submit()}
            />
            {error ? <Text style={styles.error} accessibilityLiveRegion="assertive">{error}</Text> : null}
            <TouchableOpacity
              style={[styles.button, saving && styles.buttonDisabled]}
              onPress={() => void submit()}
              disabled={saving}
              accessibilityRole="button"
              accessibilityState={{ disabled: saving, busy: saving }}
            >
              {saving ? <ActivityIndicator color="#FFFFFF" /> : <Text style={styles.buttonText}>{t('mobile:authLinks.save')}</Text>}
            </TouchableOpacity>
          </>
        )}
        <TouchableOpacity style={styles.secondaryButton} onPress={() => router.replace('/(auth)/login')} accessibilityRole="button">
          <Text style={styles.secondaryButtonText}>{t('mobile:forgotPassword.backToSignIn')}</Text>
        </TouchableOpacity>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}
