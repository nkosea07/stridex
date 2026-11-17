import { View, Text, TextInput, TouchableOpacity, StyleSheet, KeyboardAvoidingView, Platform, Alert } from 'react-native';
import { Link, router } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { useState } from 'react';
import { useAuthStore } from '@/store/auth';
import { api, ApiRequestError } from '@/lib/api';
import { useTranslation } from 'react-i18next';

export default function LoginScreen() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const { login } = useAuthStore();
  const { t } = useTranslation();
  const [isRecovering, setIsRecovering] = useState(false);

  const handleRecovery = async () => {
    if (!email || !password) {
      Alert.alert(t('mobile:login.recoveryTitle'), t('mobile:login.recoveryCredentialsRequired'));
      return;
    }
    setIsRecovering(true);
    try {
      await api.cancelDeletionWithPassword(email.trim(), password);
      Alert.alert(t('mobile:login.recoveryTitle'), t('mobile:login.recoveryResult'));
    } catch {
      Alert.alert(t('mobile:login.recoveryTitle'), t('mobile:login.recoveryError'));
    } finally {
      setIsRecovering(false);
    }
  };

  const resendVerification = async () => {
    try {
      await api.resendVerification(email.trim());
      Alert.alert(t('mobile:login.verifyTitle'), t('mobile:login.verificationSent'));
    } catch (error: any) {
      Alert.alert(t('mobile:login.verifyTitle'), error?.status === 429
        ? t('mobile:login.failure.tooManyAttempts')
        : t('mobile:login.verificationFailed'));
    }
  };

  const handleLogin = async () => {
    if (!email || !password) {
      Alert.alert(t('mobile:ui.login.error'), t('mobile:ui.login.pleaseFillInAllFields'));
      return;
    }

    setIsLoading(true);
    try {
      const result = await login(email.trim(), password);
      if (result.kind === 'authenticated') router.replace('/(tabs)');
    } catch (error: any) {
      if (error?.code === 'ADMIN_MFA_WEB_ONLY') {
        Alert.alert(t('mobile:login.adminWebOnlyTitle'), t('mobile:login.adminWebOnly'));
        return;
      }
      if (error?.code === 'EMAIL_VERIFICATION_REQUIRED') {
        Alert.alert(t('mobile:login.verifyTitle'), t('mobile:login.verifyBody'), [
          { text: t('mobile:login.verifyLater'), style: 'cancel' },
          { text: t('mobile:login.resendVerification'), onPress: () => void resendVerification() },
        ]);
        return;
      }
      const reason = error instanceof ApiRequestError && error.isNetworkError
        ? 'network'
        : error?.status === 429 ? 'tooManyAttempts' : 'invalidCredentials';
      Alert.alert(t('mobile:ui.login.loginFailed'), t(`mobile:login.failure.${reason}`));
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView 
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      enabled={Platform.OS !== 'web'} // Disable KeyboardAvoidingView on web
    >
      <View style={styles.content}>
        {/* Header */}
        <View style={styles.header}>
          <Ionicons name="walk" size={60} color="#0F766E" />
          <Text style={styles.title}>{t('mobile:ui.login.raceTracker')}</Text>
          <Text style={styles.subtitle}>{t('mobile:ui.login.signInToContinue')}</Text>
        </View>

        {/* Form */}
        <View style={styles.form}>
          <View style={styles.inputContainer}>
            <Ionicons name="mail-outline" size={20} color="#6B7280" style={styles.inputIcon} />
            <TextInput
              style={styles.input}
              placeholder={t('mobile:ui.login.emailAddress')}
              placeholderTextColor="#9CA3AF"
              value={email}
              onChangeText={setEmail}
              keyboardType="email-address"
              autoCapitalize="none"
              autoComplete="email"
            />
          </View>

          <View style={styles.inputContainer}>
            <Ionicons name="lock-closed-outline" size={20} color="#6B7280" style={styles.inputIcon} />
            <TextInput
              style={styles.input}
              placeholder={t('mobile:ui.login.passwordLabel')}
              placeholderTextColor="#9CA3AF"
              value={password}
              onChangeText={setPassword}
              secureTextEntry={!showPassword}
              autoComplete="password"
            />
            <TouchableOpacity onPress={() => setShowPassword(!showPassword)}>
              <Ionicons 
                name={showPassword ? 'eye-off-outline' : 'eye-outline'} 
                size={20} 
                color="#6B7280" 
              />
            </TouchableOpacity>
          </View>

          <Link href={{ pathname: '/(auth)/forgot-password', params: email.trim() ? { email: email.trim() } : {} }} asChild>
            <TouchableOpacity style={styles.forgotPassword} accessibilityRole="link">
              <Text style={styles.forgotPasswordText}>{t('mobile:ui.login.forgotPassword')}</Text>
            </TouchableOpacity>
          </Link>

          <TouchableOpacity 
            style={[
              styles.signInButton, 
              isLoading && styles.signInButtonDisabled
            ]}
            onPress={handleLogin}
            disabled={isLoading}
            activeOpacity={0.8}
          >
            <Text style={styles.signInButtonText}>
              {isLoading ? t('mobile:login.signingIn') : t('mobile:login.signIn')}
            </Text>
          </TouchableOpacity>
          <TouchableOpacity onPress={handleRecovery} disabled={isLoading || isRecovering}
            accessibilityRole="button" accessibilityLabel={t('mobile:login.cancelDeletion')}>
            <Text style={styles.forgotPasswordText}>
              {isRecovering ? t('mobile:login.recovering') : t('mobile:login.cancelDeletion')}
            </Text>
          </TouchableOpacity>
        </View>

        {/* Sign Up Link */}
        <View style={styles.signUpContainer}>
          <Text style={styles.signUpText}>{t('mobile:login.noAccount')} </Text>
          <Link href="/(auth)/register" asChild>
            <TouchableOpacity>
              <Text style={styles.signUpLink}>{t('mobile:ui.login.signUp')}</Text>
            </TouchableOpacity>
          </Link>
        </View>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#FFFFFF',
  },
  content: {
    flex: 1,
    padding: 24,
    justifyContent: 'center',
  },
  header: {
    alignItems: 'center',
    marginBottom: 40,
  },
  title: {
    fontSize: 32,
    fontWeight: 'bold',
    color: '#0F766E',
    marginTop: 12,
  },
  subtitle: {
    fontSize: 16,
    color: '#6B7280',
    marginTop: 8,
  },
  setupBox: {
    padding: 14,
    borderWidth: 1,
    borderColor: '#D1D5DB',
    borderRadius: 12,
    backgroundColor: '#F9FAFB',
    gap: 8,
  },
  setupText: {
    color: '#374151',
    fontSize: 14,
  },
  secret: {
    color: '#111827',
    fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace',
    fontWeight: '700',
  },
  setupUri: {
    color: '#6B7280',
    fontSize: 11,
  },
  mfaInput: {
    borderWidth: 1,
    borderColor: '#D1D5DB',
    borderRadius: 12,
    paddingHorizontal: 16,
    paddingVertical: 14,
    fontSize: 16,
    color: '#1F2937',
  },
  recoveryCodes: {
    padding: 16,
    borderRadius: 12,
    backgroundColor: '#F3F4F6',
    color: '#111827',
    fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace',
    lineHeight: 28,
  },
  form: {
    gap: 16,
  },
  inputContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#F3F4F6',
    borderRadius: 12,
    paddingHorizontal: 16,
    paddingVertical: 14,
  },
  inputIcon: {
    marginRight: 12,
  },
  input: {
    flex: 1,
    fontSize: 16,
    color: '#1F2937',
  },
  forgotPassword: {
    alignSelf: 'flex-end',
  },
  forgotPasswordText: {
    fontSize: 14,
    color: '#0F766E',
    fontWeight: '500',
  },
  signInButton: {
    backgroundColor: '#0F766E',
    paddingVertical: 16,
    borderRadius: 12,
    alignItems: 'center',
    marginTop: 8,
  },
  signInButtonDisabled: {
    opacity: 0.7,
  },
  signInButtonText: {
    fontSize: 16,
    fontWeight: '600',
    color: '#FFFFFF',
  },
  divider: {
    flexDirection: 'row',
    alignItems: 'center',
    marginVertical: 32,
  },
  dividerLine: {
    flex: 1,
    height: 1,
    backgroundColor: '#E5E7EB',
  },
  dividerText: {
    marginHorizontal: 16,
    fontSize: 14,
    color: '#9CA3AF',
  },
  socialButtons: {
    flexDirection: 'row',
    justifyContent: 'center',
    gap: 16,
  },
  socialButton: {
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: '#F3F4F6',
    justifyContent: 'center',
    alignItems: 'center',
  },
  signUpContainer: {
    flexDirection: 'row',
    justifyContent: 'center',
    marginTop: 32,
  },
  signUpText: {
    fontSize: 14,
    color: '#6B7280',
  },
  signUpLink: {
    fontSize: 14,
    fontWeight: '600',
    color: '#0F766E',
  },
});
