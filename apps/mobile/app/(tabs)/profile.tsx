import { View, Text, ScrollView, StyleSheet, TouchableOpacity, Image, Alert, Modal, TextInput, ActivityIndicator } from 'react-native';
import { useState } from 'react';
import { router } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { useAuthStore } from '@/store/auth';
import { useTheme } from '@/contexts/theme';
import { useTranslation } from 'react-i18next';
import { LOCALE_LABELS, SUPPORTED_LOCALES } from '@race-tracker/i18n';
import { setAppLanguage } from '@/lib/i18n';
import type { ThemePreference } from '@/types';

export default function ProfileScreen() {
  const { user, logout, isAuthenticated, updateProfile } = useAuthStore();
  const { theme, resolvedTheme, setTheme } = useTheme();
  const { t, i18n } = useTranslation();
  const currentLocale = i18n.resolvedLanguage ?? 'en';

  const cycleLanguage = async () => {
    const index = SUPPORTED_LOCALES.indexOf(currentLocale);
    const next = SUPPORTED_LOCALES[(index + 1) % SUPPORTED_LOCALES.length];
    await setAppLanguage(next);
  };
  const [profileEditorVisible, setProfileEditorVisible] = useState(false);
  const [savingProfile, setSavingProfile] = useState(false);
  const [draftProfile, setDraftProfile] = useState({ firstName: '', lastName: '', phone: '' });

  const cycleTheme = async () => {
    const order: ThemePreference[] = ['light', 'dark', 'system'];
    const currentIndex = order.indexOf(theme);
    const next = order[(currentIndex + 1) % order.length];
    await setTheme(next);
  };

  const handleLogout = () => {
    Alert.alert(
      'Sign Out',
      'Are you sure you want to sign out?',
      [
        { text: 'Cancel', style: 'cancel' },
        { 
          text: 'Sign Out', 
          style: 'destructive',
          onPress: () => {
            logout();
            router.replace('/(auth)/login');
          }
        },
      ]
    );
  };

  const openProfileEditor = () => {
    setDraftProfile({
      firstName: user?.firstName ?? '',
      lastName: user?.lastName ?? '',
      phone: user?.phone ?? '',
    });
    setProfileEditorVisible(true);
  };

  const saveProfile = async () => {
    if (!draftProfile.firstName.trim() || !draftProfile.lastName.trim()) {
      Alert.alert('Profile details required', 'Enter your first and last name before saving.');
      return;
    }

    setSavingProfile(true);
    try {
      await updateProfile({
        firstName: draftProfile.firstName.trim(),
        lastName: draftProfile.lastName.trim(),
        phone: draftProfile.phone.trim() || undefined,
      });
      setProfileEditorVisible(false);
      Alert.alert('Profile updated', 'Your profile changes have been saved.');
    } catch (error) {
      Alert.alert('Unable to update profile', error instanceof Error ? error.message : 'Please try again.');
    } finally {
      setSavingProfile(false);
    }
  };

  const unavailable = (feature: string) => {
    Alert.alert(feature, 'This feature is not available in the pilot yet.');
  };

  if (!isAuthenticated) {
    return (
      <View style={styles.authContainer}>
        <Text style={styles.authTitle}>Sign in to view your profile</Text>
        <TouchableOpacity 
          style={styles.signInButton}
          onPress={() => router.push('/(auth)/login')}
        >
          <Text style={styles.signInButtonText}>Sign In</Text>
        </TouchableOpacity>
      </View>
    );
  }

  const menuItems = [
    { icon: 'person-outline', label: 'Edit Profile', onPress: openProfileEditor },
    { icon: 'medical-outline', label: 'Emergency Contact', onPress: () => unavailable('Emergency Contact') },
    { icon: 'notifications-outline', label: 'Notifications', onPress: () => router.push('/notifications') },
    { icon: 'shield-checkmark-outline', label: 'Privacy & Security', onPress: () => router.push('/privacy') },
    { icon: 'trash-outline', label: 'Delete Account', onPress: () => router.push('/account-deletion') },
    { icon: 'help-circle-outline', label: 'Help & Support', onPress: () => unavailable('Help & Support') },
    { icon: 'document-text-outline', label: 'Terms & Conditions', onPress: () => unavailable('Terms & Conditions') },
    { icon: 'information-circle-outline', label: 'About', onPress: () => Alert.alert('Race Tracker', 'Pilot mobile app. Version 1.0.0') },
  ];

  return (
    <ScrollView style={styles.container} showsVerticalScrollIndicator={false}>
      {/* Profile Header */}
      <View style={styles.header}>
        <View style={styles.avatarContainer}>
          {user?.profileImage ? (
            <Image source={{ uri: user.profileImage }} style={styles.avatar} />
          ) : (
            <View style={styles.avatarPlaceholder}>
              <Ionicons name="person" size={40} color="#FFFFFF" />
            </View>
          )}
          <TouchableOpacity style={styles.editAvatarButton} onPress={() => unavailable('Profile photo')}>
            <Ionicons name="camera" size={16} color="#FFFFFF" />
          </TouchableOpacity>
        </View>
        <Text style={styles.name}>{user?.firstName} {user?.lastName}</Text>
        <Text style={styles.email}>{user?.email}</Text>
      </View>

      {/* Quick Stats */}
      <View style={styles.statsContainer}>
        <View style={styles.statItem}>
          <Text style={styles.statNumber}>—</Text>
          <Text style={styles.statLabel}>Races</Text>
        </View>
        <View style={styles.statDivider} />
        <View style={styles.statItem}>
          <Text style={styles.statNumber}>—</Text>
          <Text style={styles.statLabel}>Total KM</Text>
        </View>
        <View style={styles.statDivider} />
        <View style={styles.statItem}>
          <Text style={styles.statNumber}>—</Text>
          <Text style={styles.statLabel}>Badges</Text>
        </View>
      </View>

      {/* Menu Items */}
      <View style={styles.menuContainer}>
        <TouchableOpacity style={styles.menuItem} onPress={cycleTheme}>
          <View style={styles.menuItemLeft}>
            <Ionicons name="moon-outline" size={22} color="#6B7280" />
            <View>
              <Text style={styles.menuItemText}>Appearance</Text>
              <Text style={styles.menuSubText}>
                {theme === 'system'
                  ? `System (${resolvedTheme})`
                  : theme.charAt(0).toUpperCase() + theme.slice(1)}
              </Text>
            </View>
          </View>
          <Ionicons name="chevron-forward" size={20} color="#D1D5DB" />
        </TouchableOpacity>
        <TouchableOpacity style={styles.menuItem} onPress={() => void cycleLanguage()}>
          <View style={styles.menuItemLeft}>
            <Ionicons name="language-outline" size={22} color="#6B7280" />
            <View>
              <Text style={styles.menuItemText}>{t('common.language')}</Text>
              <Text style={styles.menuSubText}>{LOCALE_LABELS[currentLocale] ?? currentLocale}</Text>
            </View>
          </View>
          <Ionicons name="chevron-forward" size={20} color="#D1D5DB" />
        </TouchableOpacity>
      </View>

      <View style={styles.menuContainer}>
        {menuItems.map((item, index) => (
          <TouchableOpacity 
            key={index} 
            style={styles.menuItem}
            onPress={item.onPress}
          >
            <View style={styles.menuItemLeft}>
              <Ionicons name={item.icon as any} size={22} color="#6B7280" />
              <Text style={styles.menuItemText}>{item.label}</Text>
            </View>
            <Ionicons name="chevron-forward" size={20} color="#D1D5DB" />
          </TouchableOpacity>
        ))}
      </View>

      {/* Sign Out Button */}
      <TouchableOpacity style={styles.signOutButton} onPress={handleLogout}>
        <Ionicons name="log-out-outline" size={22} color="#DC2626" />
        <Text style={styles.signOutText}>Sign Out</Text>
      </TouchableOpacity>

      <Text style={styles.version}>Version 1.0.0</Text>

      <Modal visible={profileEditorVisible} transparent animationType="slide" onRequestClose={() => setProfileEditorVisible(false)}>
        <View style={styles.modalBackdrop}>
          <View style={styles.modalCard}>
            <Text style={styles.modalTitle}>Edit Profile</Text>
            <TextInput
              style={styles.modalInput}
              value={draftProfile.firstName}
              onChangeText={(firstName) => setDraftProfile((current) => ({ ...current, firstName }))}
              placeholder="First name"
              autoCapitalize="words"
            />
            <TextInput
              style={styles.modalInput}
              value={draftProfile.lastName}
              onChangeText={(lastName) => setDraftProfile((current) => ({ ...current, lastName }))}
              placeholder="Last name"
              autoCapitalize="words"
            />
            <TextInput
              style={styles.modalInput}
              value={draftProfile.phone}
              onChangeText={(phone) => setDraftProfile((current) => ({ ...current, phone }))}
              placeholder="Phone (optional)"
              keyboardType="phone-pad"
            />
            <View style={styles.modalActions}>
              <TouchableOpacity style={styles.modalCancelButton} onPress={() => setProfileEditorVisible(false)} disabled={savingProfile}>
                <Text style={styles.modalCancelText}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity style={styles.modalSaveButton} onPress={saveProfile} disabled={savingProfile}>
                {savingProfile ? <ActivityIndicator color="#FFFFFF" /> : <Text style={styles.modalSaveText}>Save</Text>}
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>
      
      <View style={styles.bottomSpacing} />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F3F4F6',
  },
  authContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
    backgroundColor: '#FFFFFF',
  },
  authTitle: {
    fontSize: 18,
    color: '#6B7280',
    marginBottom: 16,
  },
  signInButton: {
    backgroundColor: '#0F766E',
    paddingHorizontal: 32,
    paddingVertical: 12,
    borderRadius: 8,
  },
  signInButtonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '600',
  },
  header: {
    backgroundColor: '#0F766E',
    paddingTop: 24,
    paddingBottom: 32,
    alignItems: 'center',
  },
  avatarContainer: {
    position: 'relative',
    marginBottom: 16,
  },
  avatar: {
    width: 100,
    height: 100,
    borderRadius: 50,
    borderWidth: 4,
    borderColor: '#FFFFFF',
  },
  avatarPlaceholder: {
    width: 100,
    height: 100,
    borderRadius: 50,
    backgroundColor: 'rgba(255,255,255,0.3)',
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 4,
    borderColor: '#FFFFFF',
  },
  editAvatarButton: {
    position: 'absolute',
    bottom: 0,
    right: 0,
    backgroundColor: '#1F2937',
    width: 32,
    height: 32,
    borderRadius: 16,
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 3,
    borderColor: '#FFFFFF',
  },
  name: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#FFFFFF',
  },
  email: {
    fontSize: 14,
    color: '#A7F3D0',
    marginTop: 4,
  },
  statsContainer: {
    flexDirection: 'row',
    backgroundColor: '#FFFFFF',
    marginHorizontal: 16,
    marginTop: -20,
    borderRadius: 16,
    padding: 20,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 8,
    elevation: 3,
  },
  statItem: {
    flex: 1,
    alignItems: 'center',
  },
  statNumber: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#0F766E',
  },
  statLabel: {
    fontSize: 14,
    color: '#6B7280',
    marginTop: 4,
  },
  statDivider: {
    width: 1,
    backgroundColor: '#E5E7EB',
  },
  menuContainer: {
    backgroundColor: '#FFFFFF',
    marginHorizontal: 16,
    marginTop: 16,
    borderRadius: 16,
    overflow: 'hidden',
  },
  menuItem: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 16,
    paddingHorizontal: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#F3F4F6',
  },
  menuItemLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  menuItemText: {
    fontSize: 16,
    color: '#1F2937',
  },
  menuSubText: {
    marginTop: 2,
    fontSize: 12,
    color: '#6B7280',
  },
  signOutButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    backgroundColor: '#FEE2E2',
    marginHorizontal: 16,
    marginTop: 24,
    paddingVertical: 14,
    borderRadius: 12,
  },
  signOutText: {
    fontSize: 16,
    fontWeight: '600',
    color: '#DC2626',
  },
  version: {
    textAlign: 'center',
    color: '#9CA3AF',
    fontSize: 14,
    marginTop: 24,
  },
  bottomSpacing: {
    height: 32,
  },
  modalBackdrop: {
    flex: 1,
    justifyContent: 'flex-end',
    backgroundColor: 'rgba(0,0,0,0.45)',
  },
  modalCard: {
    backgroundColor: '#FFFFFF',
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    padding: 24,
    gap: 12,
  },
  modalTitle: {
    fontSize: 22,
    fontWeight: '700',
    color: '#1F2937',
    marginBottom: 4,
  },
  modalInput: {
    borderWidth: 1,
    borderColor: '#D1D5DB',
    borderRadius: 10,
    paddingHorizontal: 14,
    paddingVertical: 12,
    fontSize: 16,
    color: '#1F2937',
  },
  modalActions: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    gap: 12,
    marginTop: 8,
  },
  modalCancelButton: {
    paddingHorizontal: 18,
    paddingVertical: 12,
  },
  modalCancelText: {
    color: '#6B7280',
    fontWeight: '600',
  },
  modalSaveButton: {
    minWidth: 90,
    alignItems: 'center',
    backgroundColor: '#0F766E',
    borderRadius: 10,
    paddingHorizontal: 18,
    paddingVertical: 12,
  },
  modalSaveText: {
    color: '#FFFFFF',
    fontWeight: '700',
  },
});
