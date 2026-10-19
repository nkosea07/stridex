import { View, Text, ScrollView, StyleSheet, TouchableOpacity, Image, Alert, ActivityIndicator, Modal, TextInput, Switch } from 'react-native';
import { useLocalSearchParams } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { useCallback, useEffect, useRef, useState } from 'react';
import { api, getApiErrorKind, getNonEmptyRouteId, type ApiErrorKind } from '@/lib/api';
import {
  createPublicResultsPaginationState,
  loadNextPublicResultsPage,
} from '@/lib/public-results-pagination';
import { useAuthStore } from '@/store/auth';
import { getPreferredCapability, isOperationsRole } from '@/lib/capabilities';
import { useTranslation } from 'react-i18next';
import { CapabilityGate } from '@/components/CapabilityGate';
import { RequestErrorState } from '@/components/RequestErrorState';
import { formatCurrency, formatDate, formatDistanceKm, formatNumber, statusTranslationKey } from '@/lib/format';
import type { PublicRaceResult, RegistrationField } from '@/types';

const DEFAULT_BANNER = 'https://images.unsplash.com/photo-1513593771513-7b58b6c4af38?w=800';

function formatTime(value?: string) {
  if (!value) return '-';
  return String(value).slice(0, 5);
}

function formatRegistrationFee(value: unknown, currency?: string) {
  const amount = Number(value);
  if (!Number.isFinite(amount)) return '-';
  return formatCurrency(amount, currency || 'USD');
}

function statusColors(status?: string) {
  const normalized = String(status || '').toLowerCase();
  if (normalized === 'registration_open') return { bg: '#DCFCE7', text: '#16A34A' };
  if (normalized === 'registration_closed') return { bg: '#FEE2E2', text: '#DC2626' };
  if (normalized === 'in_progress') return { bg: '#DBEAFE', text: '#2563EB' };
  if (normalized === 'completed') return { bg: '#E5E7EB', text: '#4B5563' };
  return { bg: '#FEF3C7', text: '#D97706' };
}

export default function RaceDetailScreen() {
  return (
    <CapabilityGate anyOf={['ATHLETE', 'SPECTATOR']}>
      <RaceDetailScreenContent />
    </CapabilityGate>
  );
}

function RaceDetailScreenContent() {
  const { id: routeParam } = useLocalSearchParams<{ id?: string }>();
  const routeId = getNonEmptyRouteId(routeParam);
  const { user } = useAuthStore();
  const { t } = useTranslation();
  const activeCapability = getPreferredCapability(user);
  const canRegister = !isOperationsRole(user?.role) && activeCapability === 'ATHLETE';
  const canFollow = !isOperationsRole(user?.role) && activeCapability === 'SPECTATOR';
  const [race, setRace] = useState<any>(null);
  const [checkpoints, setCheckpoints] = useState<any[]>([]);
  const [publicResults, setPublicResults] = useState<PublicRaceResult[]>([]);
  const [followedParticipantIds, setFollowedParticipantIds] = useState<string[]>([]);
  const [busyFollowKeys, setBusyFollowKeys] = useState<Record<string, boolean>>({});
  const busyFollowKeysRef = useRef<Record<string, boolean>>({});
  const [raceErrorKind, setRaceErrorKind] = useState<ApiErrorKind | null>(null);
  const [publicResultsErrorKind, setPublicResultsErrorKind] = useState<ApiErrorKind | null>(null);
  const [favoritesErrorKind, setFavoritesErrorKind] = useState<ApiErrorKind | null>(null);
  const [isFollowBootstrapLoading, setIsFollowBootstrapLoading] = useState(false);
  const [participantPage, setParticipantPage] = useState(0);
  const [publicResultsRevision, setPublicResultsRevision] = useState<number | null>(null);
  const [failedParticipantPage, setFailedParticipantPage] = useState<number | null>(null);
  const [hasMoreParticipants, setHasMoreParticipants] = useState(false);
  const [isLoadingMoreParticipants, setIsLoadingMoreParticipants] = useState(false);
  const participantRequestGenerationRef = useRef(0);
  const participantPageInFlightRef = useRef(false);
  const [isLoading, setIsLoading] = useState(true);
  const [isRegistering, setIsRegistering] = useState(false);
  const [isRegisterOpen, setIsRegisterOpen] = useState(false);
  const [registrationFields, setRegistrationFields] = useState<RegistrationField[]>([]);
  const [selectedCategoryId, setSelectedCategoryId] = useState('');
  const [contact, setContact] = useState({ name: '', phone: '', relationship: 'Self' });
  const [shirtSize, setShirtSize] = useState('');
  const [teamName, setTeamName] = useState('');
  const [clubName, setClubName] = useState('');
  const [waiverAccepted, setWaiverAccepted] = useState(false);
  const [answers, setAnswers] = useState<Record<string, string>>({});

  const load = useCallback(async () => {
    if (!routeId) {
      setRace(null);
      setCheckpoints([]);
      setRaceErrorKind('not_found');
      return;
    }
    setRaceErrorKind(null);
    try {
      const [raceResponse, checkpointsResponse] = await Promise.all([
        api.getRace(routeId),
        api.getRaceCheckpoints(routeId),
      ]);
      setRace(raceResponse);
      setCheckpoints((checkpointsResponse as any[]).sort((a, b) => Number(a.orderIndex || 0) - Number(b.orderIndex || 0)));
    } catch (error) {
      setRace(null);
      setCheckpoints([]);
      setRaceErrorKind(getApiErrorKind(error));
    }
  }, [routeId]);

  const reloadRace = useCallback(async () => {
    setIsLoading(true);
    try {
      await load();
    } finally {
      setIsLoading(false);
    }
  }, [load]);

  useEffect(() => {
    void reloadRace();
  }, [reloadRace]);

  const loadFollowData = useCallback(async () => {
    const requestGeneration = ++participantRequestGenerationRef.current;
    if (!routeId || !canFollow) {
      setPublicResults([]);
      setFollowedParticipantIds([]);
      setPublicResultsErrorKind(null);
      setFavoritesErrorKind(null);
      setParticipantPage(0);
      setPublicResultsRevision(null);
      setFailedParticipantPage(null);
      setHasMoreParticipants(false);
      setIsLoadingMoreParticipants(false);
      setIsFollowBootstrapLoading(false);
      return;
    }

    setIsFollowBootstrapLoading(true);
    setPublicResultsErrorKind(null);
    setFavoritesErrorKind(null);
    setFailedParticipantPage(null);
    const [resultsResponse, favoritesResponse] = await Promise.allSettled([
      api.getPublicRaceResults(routeId, 0, 50),
      api.listFavorites(),
    ]);

    if (requestGeneration !== participantRequestGenerationRef.current) return;

    if (resultsResponse.status === 'fulfilled') {
      setPublicResults(resultsResponse.value.items);
      setParticipantPage(resultsResponse.value.page);
      setPublicResultsRevision(resultsResponse.value.revision);
      setFailedParticipantPage(null);
      setHasMoreParticipants(resultsResponse.value.page + 1 < resultsResponse.value.totalPages);
    } else {
      setPublicResults([]);
      setParticipantPage(0);
      setPublicResultsRevision(null);
      setFailedParticipantPage(0);
      setHasMoreParticipants(false);
      setPublicResultsErrorKind(getApiErrorKind(resultsResponse.reason));
    }

    if (favoritesResponse.status === 'fulfilled') {
      setFollowedParticipantIds(
        favoritesResponse.value
          .filter((favorite) => favorite.raceId === routeId)
          .map((favorite) => favorite.participantId),
      );
    } else {
      setFollowedParticipantIds([]);
      setFavoritesErrorKind(getApiErrorKind(favoritesResponse.reason));
    }
    setIsFollowBootstrapLoading(false);
  }, [canFollow, routeId]);

  useEffect(() => {
    void loadFollowData();
  }, [loadFollowData]);

  const loadMoreParticipants = useCallback(async (page = participantPage + 1) => {
    if (
      !routeId
      || !canFollow
      || !hasMoreParticipants
      || isLoadingMoreParticipants
      || participantPageInFlightRef.current
      || publicResultsRevision === null
    ) return;

    participantPageInFlightRef.current = true;
    const requestGeneration = ++participantRequestGenerationRef.current;
    setIsLoadingMoreParticipants(true);
    setPublicResultsErrorKind(null);
    setFailedParticipantPage(null);
    try {
      const current = createPublicResultsPaginationState({
        items: publicResults,
        page: participantPage,
        size: 50,
        totalElements: publicResults.length,
        totalPages: hasMoreParticipants ? participantPage + 2 : participantPage + 1,
        revision: publicResultsRevision,
      });
      const response = await loadNextPublicResultsPage(current, (requestedPage, requestedSize, revision) => (
        api.getPublicRaceResults(routeId, requestedPage, requestedSize, revision)
      ));

      if (requestGeneration !== participantRequestGenerationRef.current) return;

      setPublicResults(response.items);
      setParticipantPage(response.page);
      setPublicResultsRevision(response.revision);
      setHasMoreParticipants(response.page + 1 < response.totalPages);
    } catch (error) {
      if (requestGeneration !== participantRequestGenerationRef.current) return;
      setFailedParticipantPage(page);
      setPublicResultsErrorKind(getApiErrorKind(error));
    } finally {
      participantPageInFlightRef.current = false;
      setIsLoadingMoreParticipants(false);
    }
  }, [canFollow, hasMoreParticipants, isLoadingMoreParticipants, participantPage, publicResults, publicResultsRevision, routeId]);

  const openRegistration = async () => {
    if (!race || !canRegister) return;

    const firstCategory = (race.categories || [])[0];
    if (!firstCategory?.id) {
      Alert.alert('Registration unavailable', 'No race categories are available for registration yet.');
      return;
    }

    setSelectedCategoryId(String(firstCategory.id));
    setContact({
      name: user ? `${user.firstName} ${user.lastName}`.trim() : '',
      phone: user?.phone || '',
      relationship: 'Self',
    });
    setIsRegisterOpen(true);

    try {
      if (!routeId) return;
      const fields = await api.getRegistrationFields(routeId);
      setRegistrationFields(fields);
    } catch {
      setRegistrationFields([]);
    }
  };

  const submitRegistration = async () => {
    if (!selectedCategoryId) {
      Alert.alert('Registration failed', 'Please select a category.');
      return;
    }
    if (race?.requireWaiver && !waiverAccepted) {
      Alert.alert('Registration failed', 'You must accept the race waiver.');
      return;
    }

    try {
      setIsRegistering(true);
    if (!routeId) return;
    const registration = await api.registerForRace(routeId, {
        categoryId: selectedCategoryId,
        emergencyContact: {
          name: contact.name,
          phone: contact.phone,
          relationship: contact.relationship,
        },
        waiverAccepted,
        shirtSize: shirtSize || undefined,
        teamName: teamName || undefined,
        clubName: clubName || undefined,
        answers: registrationFields.map((field) => ({ fieldId: field.id, value: answers[field.id] })),
      });
      setIsRegisterOpen(false);
      const status = registration.status;
      Alert.alert(
        'Registration submitted',
        status === 'waitlisted'
          ? 'You have been added to the waitlist.'
          : status === 'pending_review'
            ? 'Your registration is pending review.'
            : 'Registration completed.',
      );
    } catch (error: any) {
      Alert.alert('Registration failed', error?.message || 'Unable to register for this race.');
    } finally {
      setIsRegistering(false);
    }
  };

  const toggleFollow = async (participantId: string) => {
    if (!routeId || favoritesErrorKind || isFollowBootstrapLoading || busyFollowKeysRef.current[participantId]) return;

    busyFollowKeysRef.current[participantId] = true;
    setBusyFollowKeys((current) => ({ ...current, [participantId]: true }));
    const isFollowing = followedParticipantIds.includes(participantId);
    try {
      if (isFollowing) {
        await api.unfollowAthlete(routeId, participantId);
        setFollowedParticipantIds((current) => current.filter((value) => value !== participantId));
      } else {
        await api.followAthlete(routeId, participantId);
        setFollowedParticipantIds((current) => [...current, participantId]);
      }
    } catch {
      Alert.alert(t(isFollowing ? 'mobile:race.unfollowFailed' : 'mobile:race.followFailed'));
    } finally {
      busyFollowKeysRef.current[participantId] = false;
      setBusyFollowKeys((current) => ({ ...current, [participantId]: false }));
    }
  };

  if (isLoading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color="#0F766E" />
      </View>
    );
  }

  if (raceErrorKind) {
    return <RequestErrorState resource="race" kind={raceErrorKind} onRetry={() => void reloadRace()} />;
  }

  if (!race) {
    return (
      <View style={styles.center}>
        <Text style={styles.emptyText}>Race not found</Text>
      </View>
    );
  }

  const statusStyle = statusColors(race.status);
  const filled = Number(race.participantCount || 0);
  const max = Number(race.maxParticipants || 0);
  const percent = max > 0 ? Math.min(100, (filled / max) * 100) : 0;

  return (
    <View style={styles.container}>
      <ScrollView showsVerticalScrollIndicator={false}>
        <Image source={{ uri: race.bannerImageUrl || DEFAULT_BANNER }} style={styles.bannerImage} />

        <View style={styles.content}>
          <View style={[styles.statusBadge, { backgroundColor: statusStyle.bg }]}>
            <Text style={[styles.statusText, { color: statusStyle.text }]}>{t(statusTranslationKey(race.status))}</Text>
          </View>

          <Text style={styles.title}>{race.name}</Text>

          <View style={styles.infoGrid}>
            <View style={styles.infoItem}>
              <Ionicons name="calendar-outline" size={20} color="#0F766E" />
              <Text style={styles.infoText}>{race.raceDate && !Number.isNaN(new Date(race.raceDate).getTime()) ? formatDate(race.raceDate) : '-'}</Text>
            </View>
            <View style={styles.infoItem}>
              <Ionicons name="time-outline" size={20} color="#0F766E" />
              <Text style={styles.infoText}>{formatTime(race.startTime)}</Text>
            </View>
            <View style={styles.infoItem}>
              <Ionicons name="location-outline" size={20} color="#0F766E" />
              <Text style={styles.infoText}>{[race.city, race.country].filter(Boolean).join(', ') || '-'}</Text>
            </View>
            <View style={styles.infoItem}>
              <Ionicons name="walk-outline" size={20} color="#0F766E" />
              <Text style={styles.infoText}>{formatDistanceKm(Number(race.distanceKm || 0))}</Text>
            </View>
          </View>

          <View style={styles.section}>
            <Text style={styles.sectionTitle}>About</Text>
            <Text style={styles.description}>{race.description || 'No description available.'}</Text>
          </View>

          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Categories</Text>
            {(race.categories || []).length ? (
              (race.categories || []).map((category: any) => (
                <View key={String(category.id)} style={styles.categoryCard}>
                  <View>
                    <Text style={styles.categoryName}>{category.name}</Text>
                    <Text style={styles.categoryDistance}>{formatDistanceKm(Number(category.distanceKm || race.distanceKm || 0))}</Text>
                  </View>
                  <Text style={styles.categoryFee}>
                    {category.registrationFee !== undefined && category.registrationFee !== null
                      ? formatRegistrationFee(category.registrationFee, race.currency)
                      : '-'}
                  </Text>
                </View>
              ))
            ) : (
              <Text style={styles.placeholderText}>No categories configured.</Text>
            )}
          </View>

          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Checkpoints</Text>
            <View style={styles.checkpointsList}>
              {checkpoints.length ? (
                checkpoints.map((checkpoint, index) => (
                  <View key={String(checkpoint.id)} style={styles.checkpointItem}>
                    <View style={styles.checkpointMarker}>
                      <View
                        style={[
                          styles.checkpointDot,
                          index === 0 && styles.checkpointDotStart,
                          index === checkpoints.length - 1 && styles.checkpointDotFinish,
                        ]}
                      />
                      {index < checkpoints.length - 1 && <View style={styles.checkpointLine} />}
                    </View>
                    <View style={styles.checkpointInfo}>
                      <Text style={styles.checkpointName}>{checkpoint.name}</Text>
                      <Text style={styles.checkpointDetails}>
                        {formatDistanceKm(Number(checkpoint.distanceFromStartKm || 0))}
                        {checkpoint.cutoffTime ? ` • Cutoff: ${checkpoint.cutoffTime}` : ''}
                      </Text>
                    </View>
                  </View>
                ))
              ) : (
                <Text style={styles.placeholderText}>No checkpoints configured.</Text>
              )}
            </View>
          </View>

          <View style={styles.section}>
            <Text style={styles.sectionTitle}>Participants</Text>
            <View style={styles.participantsCard}>
              <Text style={styles.participantsCount}>
                {formatNumber(filled)} {max > 0 ? `/ ${formatNumber(max)}` : ''}
              </Text>
              <Text style={styles.participantsLabel}>registered</Text>
              <View style={styles.progressBar}>
                <View style={[styles.progressFill, { width: `${percent}%` }]} />
              </View>
            </View>
          </View>

          {canFollow && (publicResults.length > 0 || publicResultsErrorKind || favoritesErrorKind) && (
            <View style={styles.section}>
              <Text style={styles.sectionTitle}>{t('mobile:race.followAthletesTitle')}</Text>
              <Text style={styles.followDescription}>{t('mobile:race.followAthletesDescription')}</Text>
              {publicResultsErrorKind && (
                <RequestErrorState
                  resource="results"
                  kind={publicResultsErrorKind}
                  onRetry={() => void (failedParticipantPage && failedParticipantPage > 0 ? loadMoreParticipants(failedParticipantPage) : loadFollowData())}
                  compact
                />
              )}
              {publicResults.length ? (
                <>
                  <View style={styles.followList}>
                    {publicResults.map((result) => {
                      const isFollowing = followedParticipantIds.includes(result.participantId);
                      const isBusy = Boolean(busyFollowKeys[result.participantId]);
                      const followDisabled = isBusy || Boolean(favoritesErrorKind) || isFollowBootstrapLoading;
                      return (
                        <View key={result.participantId} style={styles.followRow}>
                          <View style={styles.followDetails}>
                            <Text style={styles.followName}>
                              {result.participantName || t('mobile:following.athlete')}
                              {result.bibNumber ? ` #${result.bibNumber}` : ''}
                            </Text>
                            {result.status && <Text style={styles.followStatus}>{t(statusTranslationKey(result.status), { defaultValue: result.status })}</Text>}
                          </View>
                          <TouchableOpacity
                            style={[styles.followButton, isFollowing && styles.followButtonActive]}
                            onPress={() => void toggleFollow(result.participantId)}
                            disabled={followDisabled}
                            accessibilityRole="button"
                            accessibilityLabel={`${t(isFollowing ? 'mobile:race.unfollow' : 'mobile:race.follow')} ${result.participantName || t('mobile:following.athlete')}`}
                            accessibilityHint={t('mobile:race.followControlHint')}
                            accessibilityState={{ selected: isFollowing, busy: isBusy, disabled: followDisabled }}
                          >
                            {isBusy ? (
                              <ActivityIndicator size="small" color={isFollowing ? '#FFFFFF' : '#0F766E'} />
                            ) : (
                              <Text style={[styles.followButtonText, isFollowing && styles.followButtonTextActive]}>
                                {isFollowing ? t('mobile:race.unfollow') : t('mobile:race.follow')}
                              </Text>
                            )}
                          </TouchableOpacity>
                        </View>
                      );
                    })}
                  </View>
                  {hasMoreParticipants && (
                    <TouchableOpacity
                      style={styles.loadMoreButton}
                      onPress={() => void loadMoreParticipants()}
                      disabled={isLoadingMoreParticipants}
                      accessibilityRole="button"
                      accessibilityLabel={t('mobile:race.loadMoreAthletes')}
                      accessibilityState={{ busy: isLoadingMoreParticipants, disabled: isLoadingMoreParticipants }}
                    >
                      {isLoadingMoreParticipants ? <ActivityIndicator size="small" color="#0F766E" /> : <Text style={styles.loadMoreText}>{t('mobile:race.loadMoreAthletes')}</Text>}
                    </TouchableOpacity>
                  )}
                </>
              ) : !publicResultsErrorKind ? (
                <Text style={styles.placeholderText}>{t('mobile:race.noPublicAthletes')}</Text>
              ) : null}
              {favoritesErrorKind && (
                <RequestErrorState resource="favorites" kind={favoritesErrorKind} onRetry={() => void loadFollowData()} compact />
              )}
            </View>
          )}

          <View style={styles.bottomSpacing} />
        </View>
      </ScrollView>

      <View style={styles.registerContainer}>
        {canRegister ? (
          <TouchableOpacity style={styles.registerButton} onPress={openRegistration} disabled={isRegistering} accessibilityRole="button" accessibilityLabel={t('mobile:race.registerNow')} accessibilityState={{ busy: isRegistering, disabled: isRegistering }}>
            <Text style={styles.registerButtonText}>{isRegistering ? t('mobile:race.registering') : t('mobile:race.registerNow')}</Text>
          </TouchableOpacity>
        ) : (
          <Text style={styles.registrationNotice}>
            {user ? t('mobile:race.spectatorOnly') : t('mobile:race.athleteSignIn')}
          </Text>
        )}
      </View>

      <Modal visible={isRegisterOpen} animationType="slide" transparent onRequestClose={() => setIsRegisterOpen(false)}>
        <View style={styles.modalOverlay}>
          <View style={styles.modalCard}>
            <ScrollView showsVerticalScrollIndicator={false}>
              <Text style={styles.modalTitle}>Register for {race?.name}</Text>

              <Text style={styles.fieldLabel}>Category</Text>
              <View style={styles.optionRow}>
                {(race?.categories || []).map((category: any) => (
                  <TouchableOpacity
                    key={String(category.id)}
                    style={[styles.optionChip, selectedCategoryId === String(category.id) && styles.optionChipActive]}
                    onPress={() => setSelectedCategoryId(String(category.id))}
                  >
                    <Text style={[styles.optionChipText, selectedCategoryId === String(category.id) && styles.optionChipTextActive]}>
                      {category.name}
                    </Text>
                  </TouchableOpacity>
                ))}
              </View>

              <Text style={styles.fieldLabel}>Emergency contact name</Text>
              <TextInput
                style={styles.input}
                value={contact.name}
                onChangeText={(value) => setContact((prev) => ({ ...prev, name: value }))}
                placeholder="Full name"
                placeholderTextColor="#9CA3AF"
              />

              <Text style={styles.fieldLabel}>Emergency contact phone</Text>
              <TextInput
                style={styles.input}
                value={contact.phone}
                onChangeText={(value) => setContact((prev) => ({ ...prev, phone: value }))}
                placeholder="Phone number"
                keyboardType="phone-pad"
                placeholderTextColor="#9CA3AF"
              />

              <Text style={styles.fieldLabel}>Relationship</Text>
              <TextInput
                style={styles.input}
                value={contact.relationship}
                onChangeText={(value) => setContact((prev) => ({ ...prev, relationship: value }))}
                placeholder="e.g. Spouse"
                placeholderTextColor="#9CA3AF"
              />

              <Text style={styles.fieldLabel}>Shirt size (optional)</Text>
              <TextInput
                style={styles.input}
                value={shirtSize}
                onChangeText={setShirtSize}
                placeholder="e.g. M"
                placeholderTextColor="#9CA3AF"
              />

              <Text style={styles.fieldLabel}>Team name (optional)</Text>
              <TextInput
                style={styles.input}
                value={teamName}
                onChangeText={setTeamName}
                placeholder="Team"
                placeholderTextColor="#9CA3AF"
              />

              <Text style={styles.fieldLabel}>Club name (optional)</Text>
              <TextInput
                style={styles.input}
                value={clubName}
                onChangeText={setClubName}
                placeholder="Club"
                placeholderTextColor="#9CA3AF"
              />

              {registrationFields.map((field) => (
                <View key={field.id}>
                  <Text style={styles.fieldLabel}>{field.label}{field.required ? ' *' : ''}</Text>
                  {field.fieldType === 'CHECKBOX' ? (
                    <View style={styles.switchRow}>
                      <Switch
                        value={answers[field.id] === 'true'}
                        onValueChange={(value) => setAnswers((prev) => ({ ...prev, [field.id]: value ? 'true' : 'false' }))}
                      />
                    </View>
                  ) : field.fieldType === 'SELECT' ? (
                    <View style={styles.optionRow}>
                      {(field.options || []).map((option) => (
                        <TouchableOpacity
                          key={option}
                          style={[styles.optionChip, answers[field.id] === option && styles.optionChipActive]}
                          onPress={() => setAnswers((prev) => ({ ...prev, [field.id]: option }))}
                        >
                          <Text style={[styles.optionChipText, answers[field.id] === option && styles.optionChipTextActive]}>
                            {option}
                          </Text>
                        </TouchableOpacity>
                      ))}
                    </View>
                  ) : (
                    <TextInput
                      style={styles.input}
                      value={answers[field.id] || ''}
                      onChangeText={(value) => setAnswers((prev) => ({ ...prev, [field.id]: value }))}
                      keyboardType={field.fieldType === 'NUMBER' ? 'numeric' : 'default'}
                      placeholderTextColor="#9CA3AF"
                    />
                  )}
                </View>
              ))}

              {race?.requireWaiver && (
                <View style={styles.waiverBox}>
                  {race?.waiverText ? <Text style={styles.waiverText}>{race.waiverText}</Text> : null}
                  <View style={styles.switchRow}>
                    <Switch value={waiverAccepted} onValueChange={setWaiverAccepted} />
                    <Text style={styles.waiverLabel}>
                      I accept the race waiver{race?.waiverVersion ? ` (${race.waiverVersion})` : ''}
                    </Text>
                  </View>
                </View>
              )}

              <View style={styles.modalActions}>
                <TouchableOpacity style={styles.modalCancel} onPress={() => setIsRegisterOpen(false)} disabled={isRegistering}>
                  <Text style={styles.modalCancelText}>Cancel</Text>
                </TouchableOpacity>
                <TouchableOpacity style={styles.modalConfirm} onPress={submitRegistration} disabled={isRegistering}>
                  <Text style={styles.modalConfirmText}>{isRegistering ? 'Submitting...' : 'Submit'}</Text>
                </TouchableOpacity>
              </View>
            </ScrollView>
          </View>
        </View>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#FFFFFF',
  },
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#FFFFFF',
  },
  emptyText: {
    color: '#6B7280',
    fontSize: 16,
  },
  bannerImage: {
    width: '100%',
    height: 250,
  },
  content: {
    padding: 20,
    marginTop: -24,
    backgroundColor: '#FFFFFF',
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
  },
  statusBadge: {
    alignSelf: 'flex-start',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 20,
    marginBottom: 12,
  },
  statusText: {
    fontSize: 12,
    fontWeight: '600',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#1F2937',
    marginBottom: 16,
  },
  infoGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 16,
    marginBottom: 24,
  },
  infoItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    width: '45%',
  },
  infoText: {
    fontSize: 14,
    color: '#6B7280',
  },
  section: {
    marginBottom: 24,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#1F2937',
    marginBottom: 12,
  },
  description: {
    fontSize: 15,
    color: '#4B5563',
    lineHeight: 24,
  },
  categoryCard: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: '#F9FAFB',
    padding: 16,
    borderRadius: 12,
    marginBottom: 8,
  },
  categoryName: {
    fontSize: 16,
    fontWeight: '500',
    color: '#1F2937',
  },
  categoryDistance: {
    fontSize: 14,
    color: '#6B7280',
    marginTop: 2,
  },
  categoryFee: {
    fontSize: 18,
    fontWeight: '600',
    color: '#0F766E',
  },
  placeholderText: {
    color: '#6B7280',
    fontSize: 14,
  },
  checkpointsList: {},
  checkpointItem: {
    flexDirection: 'row',
    minHeight: 60,
  },
  checkpointMarker: {
    width: 24,
    alignItems: 'center',
  },
  checkpointDot: {
    width: 12,
    height: 12,
    borderRadius: 6,
    backgroundColor: '#D1D5DB',
    borderWidth: 2,
    borderColor: '#FFFFFF',
  },
  checkpointDotStart: {
    backgroundColor: '#22C55E',
  },
  checkpointDotFinish: {
    backgroundColor: '#0F766E',
  },
  checkpointLine: {
    flex: 1,
    width: 2,
    backgroundColor: '#E5E7EB',
  },
  checkpointInfo: {
    flex: 1,
    paddingLeft: 12,
    paddingBottom: 16,
  },
  checkpointName: {
    fontSize: 15,
    fontWeight: '500',
    color: '#1F2937',
  },
  checkpointDetails: {
    fontSize: 13,
    color: '#6B7280',
    marginTop: 2,
  },
  participantsCard: {
    backgroundColor: '#F9FAFB',
    borderRadius: 12,
    padding: 16,
  },
  participantsCount: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#1F2937',
  },
  participantsLabel: {
    fontSize: 14,
    color: '#6B7280',
    marginTop: 2,
  },
  followDescription: {
    color: '#6B7280',
    fontSize: 14,
    lineHeight: 20,
    marginBottom: 12,
  },
  followList: {
    gap: 8,
  },
  followRow: {
    alignItems: 'center',
    backgroundColor: '#F9FAFB',
    borderRadius: 12,
    flexDirection: 'row',
    gap: 12,
    padding: 12,
  },
  followDetails: {
    flex: 1,
    gap: 2,
  },
  followName: {
    color: '#1F2937',
    fontSize: 15,
    fontWeight: '600',
  },
  followStatus: {
    color: '#6B7280',
    fontSize: 12,
    textTransform: 'capitalize',
  },
  followButton: {
    alignItems: 'center',
    borderColor: '#0F766E',
    borderRadius: 10,
    borderWidth: 1,
    minWidth: 82,
    paddingHorizontal: 10,
    paddingVertical: 9,
  },
  followButtonActive: {
    backgroundColor: '#0F766E',
  },
  followButtonText: {
    color: '#0F766E',
    fontSize: 12,
    fontWeight: '700',
  },
  followButtonTextActive: {
    color: '#FFFFFF',
  },
  loadMoreButton: {
    alignItems: 'center',
    borderColor: '#99F6E4',
    borderRadius: 10,
    borderWidth: 1,
    flexDirection: 'row',
    justifyContent: 'center',
    marginTop: 12,
    minHeight: 42,
    paddingHorizontal: 12,
  },
  loadMoreText: {
    color: '#0F766E',
    fontSize: 13,
    fontWeight: '700',
  },
  progressBar: {
    height: 8,
    borderRadius: 999,
    backgroundColor: '#E5E7EB',
    marginTop: 12,
    overflow: 'hidden',
  },
  progressFill: {
    height: '100%',
    backgroundColor: '#0F766E',
  },
  registerContainer: {
    padding: 16,
    borderTopWidth: 1,
    borderTopColor: '#E5E7EB',
    backgroundColor: '#FFFFFF',
  },
  registerButton: {
    backgroundColor: '#0F766E',
    paddingVertical: 16,
    borderRadius: 12,
    alignItems: 'center',
  },
  registerButtonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '600',
  },
  registrationNotice: {
    color: '#6B7280',
    fontSize: 14,
    lineHeight: 20,
    textAlign: 'center',
  },
  bottomSpacing: {
    height: 24,
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'flex-end',
  },
  modalCard: {
    backgroundColor: '#FFFFFF',
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    paddingHorizontal: 20,
    paddingTop: 20,
    paddingBottom: 32,
    maxHeight: '90%',
  },
  modalTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#1F2937',
    marginBottom: 16,
  },
  fieldLabel: {
    fontSize: 13,
    fontWeight: '600',
    color: '#374151',
    marginTop: 12,
    marginBottom: 6,
  },
  input: {
    borderWidth: 1,
    borderColor: '#E5E7EB',
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 15,
    color: '#1F2937',
    backgroundColor: '#F9FAFB',
  },
  optionRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  optionChip: {
    borderWidth: 1,
    borderColor: '#E5E7EB',
    borderRadius: 999,
    paddingHorizontal: 14,
    paddingVertical: 8,
    backgroundColor: '#F9FAFB',
  },
  optionChipActive: {
    borderColor: '#0F766E',
    backgroundColor: '#CCFBF1',
  },
  optionChipText: {
    fontSize: 14,
    color: '#4B5563',
  },
  optionChipTextActive: {
    color: '#0F766E',
    fontWeight: '600',
  },
  switchRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    marginTop: 4,
  },
  waiverBox: {
    marginTop: 16,
    borderWidth: 1,
    borderColor: '#FCD34D',
    backgroundColor: '#FFFBEB',
    borderRadius: 12,
    padding: 12,
  },
  waiverText: {
    fontSize: 13,
    color: '#92400E',
    marginBottom: 8,
  },
  waiverLabel: {
    flex: 1,
    fontSize: 13,
    fontWeight: '600',
    color: '#92400E',
  },
  modalActions: {
    flexDirection: 'row',
    gap: 12,
    marginTop: 24,
  },
  modalCancel: {
    flex: 1,
    borderWidth: 1,
    borderColor: '#E5E7EB',
    borderRadius: 12,
    paddingVertical: 14,
    alignItems: 'center',
  },
  modalCancelText: {
    fontSize: 15,
    fontWeight: '600',
    color: '#4B5563',
  },
  modalConfirm: {
    flex: 1,
    backgroundColor: '#0F766E',
    borderRadius: 12,
    paddingVertical: 14,
    alignItems: 'center',
  },
  modalConfirmText: {
    fontSize: 15,
    fontWeight: '600',
    color: '#FFFFFF',
  },
});
