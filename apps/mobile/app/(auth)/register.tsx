import { View, Text, TextInput, TouchableOpacity, StyleSheet, ScrollView, KeyboardAvoidingView, Platform, Alert } from 'react-native';
import { Link, router } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { API_CONTRACT } from '@race-tracker/contracts';
import { useAuthStore } from '@/store/auth';
import type { UserCapability } from '@/types';

const experienceOptions: {
  key: 'athlete' | 'spectator' | 'both';
  capabilities: UserCapability[];
  icon: keyof typeof Ionicons.glyphMap;
  titleKey: string;
  descriptionKey: string;
}[] = [
  {
    key: 'athlete',
    capabilities: ['ATHLETE'],
    icon: 'walk-outline',
    titleKey: 'mobile:register.joinRace',
    descriptionKey: 'mobile:register.joinRaceDescription',
  },
  {
    key: 'spectator',
    capabilities: ['SPECTATOR'],
    icon: 'eye-outline',
    titleKey: 'mobile:register.followRaces',
    descriptionKey: 'mobile:register.followRacesDescription',
  },
  {
    key: 'both',
    capabilities: ['ATHLETE', 'SPECTATOR'],
    icon: 'people-outline',
    titleKey: 'mobile:register.both',
    descriptionKey: 'mobile:register.bothDescription',
  },
];

export default function RegisterScreen() {
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [acceptedPrivacy, setAcceptedPrivacy] = useState(false);
  const [experiences, setExperiences] = useState<UserCapability[]>(['ATHLETE']);
  const { register } = useAuthStore();
  const { t } = useTranslation();

  const selectExperience = (capabilities: UserCapability[]) => {
    setExperiences(capabilities);
  };

  const handleRegister = async () => {
    console.log('[Register] Attempting registration for:', email);
    
    if (!firstName || !lastName || !email || !password || !confirmPassword) {
      console.warn('[Register] Validation failed: missing required fields');
      Alert.alert('Error', 'Please fill in all required fields');
      return;
    }

    if (password !== confirmPassword) {
      console.warn('[Register] Validation failed: password mismatch');
      Alert.alert('Error', 'Passwords do not match');
      return;
    }

    if (password.length < 8) {
      console.warn('[Register] Validation failed: password too short');
      Alert.alert('Error', 'Password must be at least 8 characters');
      return;
    }

    if (!acceptedPrivacy) {
      Alert.alert('Error', 'Please accept the Privacy Policy to continue');
      return;
    }

    setIsLoading(true);
    try {
      console.log('[Register] Calling authStore.register...');
      const response = await register({
        firstName: firstName.trim(), 
        lastName: lastName.trim(), 
        email: email.trim(), 
        phone: phone.trim(), 
        password,
        privacyPolicyAccepted: true,
        privacyPolicyVersion: API_CONTRACT.legal.privacyPolicyVersion,
        experiences,
      });

      if (response.requiresEmailVerification || !response.token) {
        console.log('[Register] Verification required. Redirecting to login...');
        Alert.alert(
          'Verify Your Email',
          'Account created. Please verify your email before signing in.',
          [{ text: 'OK', onPress: () => router.replace('/(auth)/login') }],
        );
        return;
      }

      console.log('[Register] Registration successful. Navigating to (tabs)...');
      setTimeout(() => {
        router.replace('/(tabs)');
      }, 0);
      
    } catch (error: any) {
      console.error('[Register] Registration failed:', error);
      Alert.alert('Error', error?.message || 'Registration failed. Please try again.');
    } finally {
      console.log('[Register] Registration process completed');
      setIsLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView 
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      enabled={Platform.OS !== 'web'}
    >
      <ScrollView 
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
      >
        {/* Header */}
        <View style={styles.header}>
          <TouchableOpacity style={styles.backButton} onPress={() => router.back()}>
            <Ionicons name="arrow-back" size={24} color="#1F2937" />
          </TouchableOpacity>
          <Text style={styles.title}>Create Account</Text>
          <Text style={styles.subtitle}>Join thousands of athletes tracking their races</Text>
        </View>

        {/* Form */}
        <View style={styles.form}>
          <View style={styles.onboardingSection}>
            <Text style={styles.onboardingTitle}>{t('mobile:register.onboardingTitle')}</Text>
            <Text style={styles.onboardingSubtitle}>{t('mobile:register.onboardingSubtitle')}</Text>
            <View style={styles.experienceCards}>
              {experienceOptions.map((option) => {
                const selected = option.capabilities.length === experiences.length
                  && option.capabilities.every((capability) => experiences.includes(capability));
                return (
                  <TouchableOpacity
                    key={option.key}
                    style={[styles.experienceCard, selected && styles.experienceCardSelected]}
                    onPress={() => selectExperience(option.capabilities)}
                    accessibilityRole="radio"
                    accessibilityState={{ selected }}
                  >
                    <View style={[styles.experienceIcon, selected && styles.experienceIconSelected]}>
                      <Ionicons name={option.icon} size={22} color={selected ? '#FFFFFF' : '#0F766E'} />
                    </View>
                    <View style={styles.experienceCopy}>
                      <Text style={styles.experienceTitle}>{t(option.titleKey)}</Text>
                      <Text style={styles.experienceDescription}>{t(option.descriptionKey)}</Text>
                    </View>
                    <Ionicons
                      name={selected ? 'checkmark-circle' : 'ellipse-outline'}
                      size={24}
                      color={selected ? '#0F766E' : '#9CA3AF'}
                    />
                  </TouchableOpacity>
                );
              })}
            </View>
          </View>

          <View style={styles.row}>
            <View style={[styles.inputContainer, styles.halfInput]}>
              <Ionicons name="person-outline" size={20} color="#6B7280" style={styles.inputIcon} />
              <TextInput
                style={styles.input}
                placeholder="First Name"
                placeholderTextColor="#9CA3AF"
                value={firstName}
                onChangeText={setFirstName}
                autoCapitalize="words"
              />
            </View>
            <View style={[styles.inputContainer, styles.halfInput]}>
              <TextInput
                style={styles.input}
                placeholder="Last Name"
                placeholderTextColor="#9CA3AF"
                value={lastName}
                onChangeText={setLastName}
                autoCapitalize="words"
              />
            </View>
          </View>

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
            <Ionicons name="call-outline" size={20} color="#6B7280" style={styles.inputIcon} />
            <TextInput
              style={styles.input}
              placeholder="Phone number (optional)"
              placeholderTextColor="#9CA3AF"
              value={phone}
              onChangeText={setPhone}
              keyboardType="phone-pad"
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
            />
            <TouchableOpacity onPress={() => setShowPassword(!showPassword)}>
              <Ionicons 
                name={showPassword ? 'eye-off-outline' : 'eye-outline'} 
                size={20} 
                color="#6B7280" 
              />
            </TouchableOpacity>
          </View>

          <View style={styles.inputContainer}>
            <Ionicons name="lock-closed-outline" size={20} color="#6B7280" style={styles.inputIcon} />
            <TextInput
              style={styles.input}
              placeholder="Confirm Password"
              placeholderTextColor="#9CA3AF"
              value={confirmPassword}
              onChangeText={setConfirmPassword}
              secureTextEntry={!showPassword}
            />
          </View>

          <View style={styles.termsContainer}>
            <TouchableOpacity
              style={styles.checkboxRow}
              onPress={() => setAcceptedPrivacy((value) => !value)}
              accessibilityRole="checkbox"
              accessibilityState={{ checked: acceptedPrivacy }}
            >
              <Ionicons
                name={acceptedPrivacy ? 'checkbox' : 'square-outline'}
                size={20}
                color={acceptedPrivacy ? '#0F766E' : '#6B7280'}
              />
              <Text style={styles.termsText}>
                {"I have read and accept the "}
                <Text
                  style={styles.termsLink}
                  onPress={() => router.push('/privacy')}
                >
                  Privacy Policy
                </Text>
                .
              </Text>
            </TouchableOpacity>
          </View>

          <TouchableOpacity 
            style={[styles.signUpButton, isLoading && styles.signUpButtonDisabled]}
            onPress={handleRegister}
            disabled={isLoading}
          >
            <Text style={styles.signUpButtonText}>
              {isLoading ? 'Creating Account...' : 'Create Account'}
            </Text>
          </TouchableOpacity>
        </View>

        {/* Sign In Link */}
        <View style={styles.signInContainer}>
          <Text style={styles.signInText}>Already have an account? </Text>
          <Link href="/(auth)/login" asChild>
            <TouchableOpacity>
              <Text style={styles.signInLink}>Sign In</Text>
            </TouchableOpacity>
          </Link>
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#FFFFFF',
  },
  scrollContent: {
    flexGrow: 1,
    padding: 24,
    paddingTop: 60,
  },
  header: {
    marginBottom: 32,
  },
  backButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: '#F3F4F6',
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 24,
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#1F2937',
  },
  subtitle: {
    fontSize: 16,
    color: '#6B7280',
    marginTop: 8,
  },
  form: {
    gap: 16,
  },
  onboardingSection: {
    gap: 8,
    marginBottom: 4,
  },
  onboardingTitle: {
    color: '#1F2937',
    fontSize: 18,
    fontWeight: '700',
  },
  onboardingSubtitle: {
    color: '#6B7280',
    fontSize: 14,
    lineHeight: 20,
  },
  experienceCards: {
    gap: 10,
    marginTop: 4,
  },
  experienceCard: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    padding: 14,
    borderWidth: 1,
    borderColor: '#E5E7EB',
    borderRadius: 14,
    backgroundColor: '#FFFFFF',
  },
  experienceCardSelected: {
    borderColor: '#0F766E',
    backgroundColor: '#F0FDFA',
  },
  experienceIcon: {
    width: 42,
    height: 42,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 21,
    backgroundColor: '#CCFBF1',
  },
  experienceIconSelected: {
    backgroundColor: '#0F766E',
  },
  experienceCopy: {
    flex: 1,
    gap: 2,
  },
  experienceTitle: {
    color: '#1F2937',
    fontSize: 15,
    fontWeight: '700',
  },
  experienceDescription: {
    color: '#6B7280',
    fontSize: 13,
    lineHeight: 18,
  },
  row: {
    flexDirection: 'row',
    gap: 12,
  },
  inputContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#F3F4F6',
    borderRadius: 12,
    paddingHorizontal: 16,
    paddingVertical: 14,
  },
  halfInput: {
    flex: 1,
  },
  inputIcon: {
    marginRight: 12,
  },
  input: {
    flex: 1,
    fontSize: 16,
    color: '#1F2937',
  },
  termsContainer: {
    marginTop: 8,
  },
  checkboxRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 8,
  },
  termsText: {
    flex: 1,
    fontSize: 14,
    color: '#6B7280',
    lineHeight: 20,
  },
  termsLink: {
    color: '#0F766E',
    fontWeight: '500',
  },
  signUpButton: {
    backgroundColor: '#0F766E',
    paddingVertical: 16,
    borderRadius: 12,
    alignItems: 'center',
    marginTop: 8,
  },
  signUpButtonDisabled: {
    opacity: 0.7,
  },
  signUpButtonText: {
    fontSize: 16,
    fontWeight: '600',
    color: '#FFFFFF',
  },
  signInContainer: {
    flexDirection: 'row',
    justifyContent: 'center',
    marginTop: 32,
    marginBottom: 24,
  },
  signInText: {
    fontSize: 14,
    color: '#6B7280',
  },
  signInLink: {
    fontSize: 14,
    fontWeight: '600',
    color: '#0F766E',
  },
});
