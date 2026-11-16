import { useState } from 'react';
import { ActivityIndicator, KeyboardAvoidingView, Platform, StyleSheet, Text, TextInput, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { router, useLocalSearchParams } from 'expo-router';
import { useTranslation } from 'react-i18next';
import { api, ApiRequestError } from '@/lib/api';

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export default function ForgotPasswordScreen() {
  const { t } = useTranslation();
  const params = useLocalSearchParams<{ email?: string }>();
  const [email, setEmail] = useState(typeof params.email === 'string' ? params.email : '');
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    const value = email.trim();
    if (!EMAIL_PATTERN.test(value)) {
      setError(t('mobile:forgotPassword.invalidEmail'));
      return;
    }
    setError(null);
    setSending(true);
    try {
      await api.forgotPassword(value);
      setSent(true);
    } catch (caught) {
      if (caught instanceof ApiRequestError && caught.status === 429) {
        setError(t('mobile:forgotPassword.tooMany'));
      } else if (caught instanceof ApiRequestError && caught.isNetworkError) {
        setError(t('mobile:forgotPassword.network'));
      } else {
        setError(t('mobile:forgotPassword.failed'));
      }
    } finally {
      setSending(false);
    }
  };

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      enabled={Platform.OS !== 'web'}
    >
      <View style={styles.content}>
        <TouchableOpacity onPress={() => router.back()} style={styles.back}
          accessibilityRole="button" accessibilityLabel={t('mobile:forgotPassword.back')}>
          <Ionicons name="arrow-back" size={24} color="#111827" />
        </TouchableOpacity>
        <Ionicons name="key-outline" size={48} color="#0F766E" />
        <Text style={styles.title} accessibilityRole="header">{t('mobile:forgotPassword.title')}</Text>
        {sent ? (
          <View accessibilityLiveRegion="polite">
            <Text style={styles.body}>{t('mobile:forgotPassword.sent')}</Text>
            <TouchableOpacity style={styles.button} onPress={() => router.replace('/(auth)/login')} accessibilityRole="button">
              <Text style={styles.buttonText}>{t('mobile:forgotPassword.backToSignIn')}</Text>
            </TouchableOpacity>
          </View>
        ) : (
          <>
            <Text style={styles.body}>{t('mobile:forgotPassword.intro')}</Text>
            <TextInput
              style={styles.input}
              value={email}
              onChangeText={setEmail}
              placeholder={t('mobile:ui.login.emailAddress')}
              placeholderTextColor="#9CA3AF"
              keyboardType="email-address"
              autoCapitalize="none"
              autoComplete="email"
              accessibilityLabel={t('mobile:ui.login.emailAddress')}
            />
            {error ? <Text style={styles.error} accessibilityLiveRegion="assertive">{error}</Text> : null}
            <TouchableOpacity style={[styles.button, sending && styles.buttonDisabled]} onPress={() => void submit()}
              disabled={sending} accessibilityRole="button" accessibilityState={{ busy: sending, disabled: sending }}>
              {sending ? <ActivityIndicator color="#FFFFFF" /> : <Text style={styles.buttonText}>{t('mobile:forgotPassword.send')}</Text>}
            </TouchableOpacity>
          </>
        )}
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#FFFFFF' },
  content: { flex: 1, padding: 24, justifyContent: 'center', gap: 12 },
  back: { position: 'absolute', top: 56, left: 24, padding: 4 },
  title: { fontSize: 26, fontWeight: '700', color: '#111827' },
  body: { fontSize: 15, lineHeight: 22, color: '#4B5563' },
  input: {
    borderWidth: 1,
    borderColor: '#D1D5DB',
    borderRadius: 12,
    paddingHorizontal: 16,
    paddingVertical: 14,
    fontSize: 16,
    color: '#111827',
  },
  error: { color: '#B91C1C', fontSize: 14 },
  button: {
    backgroundColor: '#0F766E',
    borderRadius: 12,
    minHeight: 52,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 8,
  },
  buttonDisabled: { opacity: 0.6 },
  buttonText: { color: '#FFFFFF', fontSize: 16, fontWeight: '600' },
});
