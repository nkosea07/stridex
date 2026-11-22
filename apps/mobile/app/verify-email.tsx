import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, ScrollView, Text, TouchableOpacity, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { router, useLocalSearchParams } from 'expo-router';
import { useTranslation } from 'react-i18next';
import { api, ApiRequestError } from '@/lib/api';
import { readLinkToken } from '@/lib/passwordRules';
import { authLinkStyles as styles } from '@/components/authLinkStyles';

type Status = 'verifying' | 'verified' | 'invalid' | 'network';

export default function VerifyEmailScreen() {
  const { t } = useTranslation();
  const params = useLocalSearchParams<{ token?: string | string[] }>();
  const [token] = useState(() => readLinkToken(params.token));
  const [status, setStatus] = useState<Status>(token ? 'verifying' : 'invalid');
  const attempted = useRef(false);

  const verify = (value: string) => {
    setStatus('verifying');
    api.verifyEmail(value)
      .then(() => setStatus('verified'))
      .catch((error) => setStatus(error instanceof ApiRequestError && error.isNetworkError ? 'network' : 'invalid'));
  };

  useEffect(() => {
    if (!token || attempted.current) return;
    attempted.current = true;
    verify(token);
  }, [token]);

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
      <Text style={styles.title} accessibilityRole="header">{t('mobile:authLinks.verifyTitle')}</Text>
      <View accessibilityLiveRegion="polite">
        {status === 'verifying' && (
          <View style={styles.status}>
            <ActivityIndicator color="#0F766E" />
            <Text style={styles.body}>{t('mobile:authLinks.verifying')}</Text>
          </View>
        )}
        {status === 'verified' && (
          <View style={styles.status}>
            <Ionicons name="checkmark-circle" size={22} color="#0F766E" />
            <Text style={[styles.body, { flex: 1 }]}>{t('mobile:authLinks.verified')}</Text>
          </View>
        )}
        {status === 'invalid' && <Text style={styles.error}>{t('mobile:authLinks.verifyInvalid')}</Text>}
        {status === 'network' && <Text style={styles.error}>{t('mobile:forgotPassword.network')}</Text>}
      </View>
      {status === 'network' && token && (
        <TouchableOpacity style={styles.button} onPress={() => verify(token)} accessibilityRole="button">
          <Text style={styles.buttonText}>{t('mobile:errors.retry')}</Text>
        </TouchableOpacity>
      )}
      {status !== 'verifying' && status !== 'network' && (
        <TouchableOpacity style={styles.button} onPress={() => router.replace('/(auth)/login')} accessibilityRole="button">
          <Text style={styles.buttonText}>{t('mobile:authLinks.signIn')}</Text>
        </TouchableOpacity>
      )}
    </ScrollView>
  );
}
