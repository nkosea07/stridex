import { View, Text, TextInput, TouchableOpacity, StyleSheet, KeyboardAvoidingView, Platform, Alert } from 'react-native';
import { Link, router } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { useState } from 'react';
import { useAuthStore } from '@/store/auth';
import { api } from '@/lib/api';

export default function LoginScreen() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [challenge, setChallenge] = useState<{ token: string; enrollmentRequired: boolean } | null>(null);
  const [setup, setSetup] = useState<{ secret: string; provisioningUri: string } | null>(null);
  const [mfaCode, setMfaCode] = useState('');
  const [recoveryCodes, setRecoveryCodes] = useState<string[]>([]);
  const { login, completeMfa } = useAuthStore();

  const handleLogin = async () => {
    if (!email || !password) {
      Alert.alert('Error', 'Please fill in all fields');
      return;
    }

    setIsLoading(true);
    try {
      const result = await login(email.trim(), password);
      if (result.kind === 'mfa') {
        setChallenge({ token: result.challengeToken, enrollmentRequired: result.enrollmentRequired });
        if (result.enrollmentRequired) setSetup(await api.beginTotpEnrollment(result.challengeToken));
      } else {
        router.replace('/(tabs)');
      }
    } catch (error: any) {
      const errorMessage = error?.message || 'Invalid email or password';
      Alert.alert('Login Failed', errorMessage === 'Email verification required'
        ? 'Verify your email before signing in.'
        : errorMessage);
    } finally {
      setIsLoading(false);
    }
  };

  const handleMfa = async () => {
    if (!challenge || !mfaCode.trim()) return;
    setIsLoading(true);
    try {
      const response = await completeMfa(challenge.token, mfaCode.trim(), challenge.enrollmentRequired);
      if (response.recoveryCodes?.length) setRecoveryCodes(response.recoveryCodes);
      else router.replace('/(tabs)');
    } catch (error: any) {
      Alert.alert('Verification failed', error?.message || 'The code was rejected or the challenge expired.');
    } finally {
      setIsLoading(false);
    }
  };

  const continueAfterRecoveryCodes = () => {
    setRecoveryCodes([]);
    router.replace('/(tabs)');
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
          <Text style={styles.title}>Race Tracker</Text>
          <Text style={styles.subtitle}>Sign in to continue</Text>
        </View>

        {/* Form */}
        {recoveryCodes.length > 0 ? (
          <View style={styles.form}>
            <Text style={styles.subtitle}>Store these one-use recovery codes somewhere safe:</Text>
            <Text selectable style={styles.recoveryCodes}>{recoveryCodes.join('\n')}</Text>
            <TouchableOpacity style={styles.signInButton} onPress={continueAfterRecoveryCodes}>
              <Text style={styles.signInButtonText}>Continue</Text>
            </TouchableOpacity>
          </View>
        ) : challenge ? (
          <View style={styles.form}>
            {setup && (
              <View style={styles.setupBox}>
                <Text style={styles.setupText}>Add Race Tracker to your authenticator with this setup key:</Text>
                <Text selectable style={styles.secret}>{setup.secret}</Text>
                <Text selectable style={styles.setupUri}>{setup.provisioningUri}</Text>
              </View>
            )}
            <TextInput
              style={styles.mfaInput}
              placeholder="Authenticator or recovery code"
              value={mfaCode}
              onChangeText={setMfaCode}
              autoCapitalize="characters"
              autoComplete="one-time-code"
              maxLength={64}
            />
            <TouchableOpacity style={[styles.signInButton, isLoading && styles.signInButtonDisabled]} onPress={handleMfa} disabled={isLoading}>
              <Text style={styles.signInButtonText}>{isLoading ? 'Verifying...' : challenge.enrollmentRequired ? 'Enable MFA and continue' : 'Verify and continue'}</Text>
            </TouchableOpacity>
            <TouchableOpacity onPress={() => { setChallenge(null); setSetup(null); setMfaCode(''); }} disabled={isLoading}>
              <Text style={styles.forgotPasswordText}>Back to sign in</Text>
            </TouchableOpacity>
          </View>
        ) : (
        <View style={styles.form}>
          <View style={styles.inputContainer}>
            <Ionicons name="mail-outline" size={20} color="#6B7280" style={styles.inputIcon} />
            <TextInput
              style={styles.input}
              placeholder="Email address"
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
              placeholder="Password"
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

          <TouchableOpacity style={styles.forgotPassword}>
            <Text style={styles.forgotPasswordText}>Forgot password?</Text>
          </TouchableOpacity>

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
              {isLoading ? 'Signing in...' : 'Sign In'}
            </Text>
          </TouchableOpacity>
        </View>
        )}

        {/* Sign Up Link */}
        <View style={styles.signUpContainer}>
          <Text style={styles.signUpText}>{"Don't have an account? "}</Text>
          <Link href="/(auth)/register" asChild>
            <TouchableOpacity>
              <Text style={styles.signUpLink}>Sign Up</Text>
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
