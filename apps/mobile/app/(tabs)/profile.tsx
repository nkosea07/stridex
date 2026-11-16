import { View, Text, ScrollView, StyleSheet, TouchableOpacity, Image, Alert, Modal, TextInput, ActivityIndicator } from 'react-native';
import { useEffect, useMemo, useState } from 'react';
import Constants from 'expo-constants';
import { api } from '@/lib/api';
import { formatNumber } from '@/lib/format';
import { router } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { useAuthStore } from '@/store/auth';
import { useTheme } from '@/contexts/theme';
import { useTranslation } from 'react-i18next';
import { LOCALE_LABELS, SUPPORTED_LOCALES } from '@race-tracker/i18n';
import { setAppLanguage } from '@/lib/i18n';
import type { AthleteHistoryEntry, ThemePreference } from '@/types';
import { getPreferredCapability, getUserCapabilities, hasCapability, isOperationsRole } from '@/lib/capabilities';

const APP_VERSION = Constants.expoConfig?.version ?? '-';

export default function ProfileScreen() {
  const { user, logout, isAuthenticated, updateProfile, updateCapabilities } = useAuthStore();
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
  const [savingCapability, setSavingCapability] = useState(false);
  const [draftProfile, setDraftProfile] = useState({ firstName: '', lastName: '', phone: '' });
  const capabilities = getUserCapabilities(user);
  const preferredCapability = getPreferredCapability(user);
  const [history, setHistory] = useState<AthleteHistoryEntry[] | null>(null);
  const isAthlete = Boolean(user && hasCapability(user, 'ATHLETE'));

  useEffect(() => {
    if (!isAuthenticated || !isAthlete) {
      setHistory(null);
      return;
    }
    let active = true;
    api.getMyHistory()
      .then((entries) => { if (active) setHistory(entries); })
      .catch(() => { if (active) setHistory(null); });
    return () => { active = false; };
  }, [isAuthenticated, isAthlete, user?.id]);

  const profileStats = useMemo(() => {
    if (!history) return null;
    const finished = history.filter((entry) => !entry.disqualified);
    return {
      races: formatNumber(finished.length),
      distance: formatNumber(finished.reduce((sum, entry) => sum + Number(entry.distanceKm ?? 0), 0), { maximumFractionDigits: 1 }),
      medals: formatNumber(finished.filter((entry) => Boolean(entry.medal)).length),
    };
  }, [history]);

  const cycleTheme = async () => {
    const order: ThemePreference[] = ['light', 'dark', 'system'];
    const currentIndex = order.indexOf(theme);
    const next = order[(currentIndex + 1) % order.length];
    await setTheme(next);
  };

  const handleLogout = () => {
    Alert.alert(
      t('mobile:ui.profile.signOut'),
      t('mobile:profileScreen.signOutConfirm'),
      [
        { text: t('mobile:ui.profile.cancel'), style: 'cancel' },
        { 
          text: t('mobile:ui.profile.signOut'), 
          style: 'destructive',
          onPress: () => {
            void logout().finally(() => router.replace('/(auth)/login'));
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
      Alert.alert(t('mobile:ui.profile.profileDetailsRequired'), t('mobile:ui.profile.enterYourFirstAndLastName'));
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
      Alert.alert(t('mobile:ui.profile.profileUpdated'), t('mobile:ui.profile.yourProfileChangesHaveBeenSaved'));
    } catch {
      Alert.alert(t('mobile:ui.profile.unableToUpdateProfile'), t('mobile:profileScreen.tryAgain'));
    } finally {
      setSavingProfile(false);
    }
  };

  const selectPreferredCapability = async (capability: 'ATHLETE' | 'SPECTATOR') => {
    if (!user || !hasCapability(user, capability) || preferredCapability === capability) return;

    setSavingCapability(true);
    try {
      await updateCapabilities({ capabilities, preferredCapability: capability });
      Alert.alert(t('mobile:capabilities.updated'));
    } catch {
      Alert.alert(
        t('mobile:capabilities.updateFailed'),
        t('mobile:profileScreen.tryAgain'),
      );
    } finally {
      setSavingCapability(false);
    }
  };

  if (!isAuthenticated) {
    return (
      <View style={styles.authContainer}>
        <Text style={styles.authTitle}>{t('mobile:ui.profile.signInToViewYourProfile')}</Text>
        <TouchableOpacity 
          style={styles.signInButton}
          onPress={() => router.push('/(auth)/login')}
        >
          <Text style={styles.signInButtonText}>{t('mobile:ui.profile.signIn')}</Text>
        </TouchableOpacity>
      </View>
    );
  }

  const menuItems = [
    { icon: 'person-outline', label: t('mobile:ui.profile.editProfile'), onPress: openProfileEditor },
    { icon: 'notifications-outline', label: t('mobile:screens.notifications'), onPress: () => router.push('/notifications') },
    { icon: 'settings-outline', label: t('mobile:settings.title'), onPress: () => router.push('/settings') },
    { icon: 'shield-checkmark-outline', label: t('legal:common.privacyPolicy'), onPress: () => router.push('/privacy') },
    { icon: 'trash-outline', label: t('account:deletion.title'), onPress: () => router.push('/account-deletion') },
    { icon: 'document-text-outline', label: t('legal:common.terms'), onPress: () => router.push('/terms') },
    { icon: 'information-circle-outline', label: t('mobile:profileScreen.about'), onPress: () => Alert.alert(t('mobile:ui.profile.raceTracker'), t('mobile:profileScreen.aboutBody', { version: APP_VERSION })) },
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
        </View>
        <Text style={styles.name}>{user?.firstName} {user?.lastName}</Text>
        <Text style={styles.email}>{user?.email}</Text>
      </View>

      {/* Quick Stats */}
      <View style={styles.statsContainer}>
        <View style={styles.statItem}>
          <Text style={styles.statNumber}>{profileStats?.races ?? '—'}</Text>
          <Text style={styles.statLabel}>{t('mobile:ui.profile.races')}</Text>
        </View>
        <View style={styles.statDivider} />
        <View style={styles.statItem}>
          <Text style={styles.statNumber}>{profileStats?.distance ?? '—'}</Text>
          <Text style={styles.statLabel}>{t('mobile:ui.profile.totalKm')}</Text>
        </View>
        <View style={styles.statDivider} />
        <View style={styles.statItem}>
          <Text style={styles.statNumber}>{profileStats?.medals ?? '—'}</Text>
          <Text style={styles.statLabel}>{t('mobile:profileScreen.medals')}</Text>
        </View>
      </View>

      {!isOperationsRole(user?.role) && capabilities.length > 0 ? (
        <View style={styles.capabilityContainer}>
          <Text style={styles.capabilityTitle}>{t('mobile:capabilities.title')}</Text>
          <Text style={styles.capabilityDescription}>{t('mobile:capabilities.description')}</Text>
          <View style={styles.capabilityCards}>
            {(['ATHLETE', 'SPECTATOR'] as const)
              .filter((capability) => capabilities.includes(capability))
              .map((capability) => {
                const isAthleteCapability = capability === 'ATHLETE';
                const isSelected = preferredCapability === capability;
                return (
                  <TouchableOpacity
                    key={capability}
                    style={[styles.capabilityCard, isSelected && styles.capabilityCardSelected]}
                    onPress={() => void selectPreferredCapability(capability)}
                    disabled={savingCapability}
                    accessibilityRole="radio"
                    accessibilityState={{ selected: isSelected, disabled: savingCapability }}
                  >
                    <Ionicons
                      name={isAthleteCapability ? 'walk-outline' : 'eye-outline'}
                      size={22}
                      color={isSelected ? '#0F766E' : '#6B7280'}
                    />
                    <View style={styles.capabilityCopy}>
                      <Text style={styles.capabilityCardTitle}>
                        {t(isAthleteCapability ? 'mobile:capabilities.athlete' : 'mobile:capabilities.spectator')}
                      </Text>
                      <Text style={styles.capabilityCardDescription}>
                        {t(isAthleteCapability ? 'mobile:capabilities.athleteDescription' : 'mobile:capabilities.spectatorDescription')}
                      </Text>
                    </View>
                    {isSelected && <Text style={styles.capabilityActive}>{t('mobile:capabilities.active')}</Text>}
                  </TouchableOpacity>
                );
              })}
          </View>
        </View>
      ) : isOperationsRole(user?.role) ? (
        <View style={styles.operationsNotice}>
          <Text style={styles.operationsNoticeTitle}>{t('mobile:home.operationsTitle')}</Text>
          <Text style={styles.operationsNoticeText}>{t('mobile:home.operationsDescription')}</Text>
        </View>
      ) : null}

      {/* Menu Items */}
      <View style={styles.menuContainer}>
        <TouchableOpacity style={styles.menuItem} onPress={cycleTheme}>
          <View style={styles.menuItemLeft}>
            <Ionicons name="moon-outline" size={22} color="#6B7280" />
            <View>
              <Text style={styles.menuItemText}>{t('mobile:ui.profile.appearance')}</Text>
              <Text style={styles.menuSubText}>
                {theme === 'system'
                  ? t('mobile:profileScreen.themeSystem', { theme: t(`mobile:profileScreen.theme.${resolvedTheme}`) })
                  : t(`mobile:profileScreen.theme.${theme}`)}
              </Text>
            </View>
          </View>
          <Ionicons name="chevron-forward" size={20} color="#D1D5DB" />
        </TouchableOpacity>
        <TouchableOpacity style={styles.menuItem} onPress={() => void cycleLanguage()}>
          <View style={styles.menuItemLeft}>
            <Ionicons name="language-outline" size={22} color="#6B7280" />
            <View>
              <Text style={styles.menuItemText}>{t('common:language')}</Text>
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
        <Text style={styles.signOutText}>{t('mobile:ui.profile.signOut')}</Text>
      </TouchableOpacity>

      <Text style={styles.version}>{t('mobile:profileScreen.version', { version: APP_VERSION })}</Text>

      <Modal visible={profileEditorVisible} transparent animationType="slide" onRequestClose={() => setProfileEditorVisible(false)}>
        <View style={styles.modalBackdrop}>
          <View style={styles.modalCard}>
            <Text style={styles.modalTitle}>{t('mobile:ui.profile.editProfile')}</Text>
            <TextInput
              style={styles.modalInput}
              value={draftProfile.firstName}
              onChangeText={(firstName) => setDraftProfile((current) => ({ ...current, firstName }))}
              placeholder={t('mobile:ui.profile.firstName')}
              autoCapitalize="words"
            />
            <TextInput
              style={styles.modalInput}
              value={draftProfile.lastName}
              onChangeText={(lastName) => setDraftProfile((current) => ({ ...current, lastName }))}
              placeholder={t('mobile:ui.profile.lastName')}
              autoCapitalize="words"
            />
            <TextInput
              style={styles.modalInput}
              value={draftProfile.phone}
              onChangeText={(phone) => setDraftProfile((current) => ({ ...current, phone }))}
              placeholder={t('mobile:ui.profile.phoneOptional')}
              keyboardType="phone-pad"
            />
            <View style={styles.modalActions}>
              <TouchableOpacity style={styles.modalCancelButton} onPress={() => setProfileEditorVisible(false)} disabled={savingProfile}>
                <Text style={styles.modalCancelText}>{t('mobile:ui.profile.cancel')}</Text>
              </TouchableOpacity>
              <TouchableOpacity style={styles.modalSaveButton} onPress={saveProfile} disabled={savingProfile}>
                {savingProfile ? <ActivityIndicator color="#FFFFFF" /> : <Text style={styles.modalSaveText}>{t('mobile:ui.profile.save')}</Text>}
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
  capabilityContainer: {
    backgroundColor: '#FFFFFF',
    marginHorizontal: 16,
    marginTop: 16,
    padding: 16,
    borderRadius: 16,
  },
  capabilityTitle: {
    color: '#1F2937',
    fontSize: 18,
    fontWeight: '700',
  },
  capabilityDescription: {
    color: '#6B7280',
    fontSize: 13,
    lineHeight: 18,
    marginTop: 6,
  },
  capabilityCards: {
    gap: 10,
    marginTop: 14,
  },
  capabilityCard: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    padding: 12,
    borderWidth: 1,
    borderColor: '#E5E7EB',
    borderRadius: 12,
  },
  capabilityCardSelected: {
    borderColor: '#0F766E',
    backgroundColor: '#F0FDFA',
  },
  capabilityCopy: {
    flex: 1,
    gap: 2,
  },
  capabilityCardTitle: {
    color: '#1F2937',
    fontSize: 15,
    fontWeight: '700',
  },
  capabilityCardDescription: {
    color: '#6B7280',
    fontSize: 12,
    lineHeight: 17,
  },
  capabilityActive: {
    color: '#0F766E',
    fontSize: 12,
    fontWeight: '700',
  },
  operationsNotice: {
    backgroundColor: '#FFFFFF',
    marginHorizontal: 16,
    marginTop: 16,
    padding: 16,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: '#FCD34D',
  },
  operationsNoticeTitle: {
    color: '#92400E',
    fontSize: 16,
    fontWeight: '700',
  },
  operationsNoticeText: {
    color: '#92400E',
    fontSize: 13,
    lineHeight: 18,
    marginTop: 6,
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
