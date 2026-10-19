import { ActivityIndicator, Alert, RefreshControl, ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Link } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { api, getApiErrorKind, type ApiErrorKind } from '@/lib/api';
import { CapabilityGate } from '@/components/CapabilityGate';
import { RequestErrorState } from '@/components/RequestErrorState';
import { formatDistanceKm, statusTranslationKey } from '@/lib/format';
import type { FavoriteAthlete } from '@/types';

export default function FollowedScreen() {
  return (
    <CapabilityGate capability="SPECTATOR">
      <FollowedScreenContent />
    </CapabilityGate>
  );
}

function FollowedScreenContent() {
  const { t } = useTranslation();
  const [favorites, setFavorites] = useState<FavoriteAthlete[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [errorKind, setErrorKind] = useState<ApiErrorKind | null>(null);
  const [activeActions, setActiveActions] = useState<Record<string, boolean>>({});
  const activeActionsRef = useRef<Record<string, boolean>>({});

  const loadFavorites = useCallback(async () => {
    setErrorKind(null);
    setIsLoading(true);
    try {
      setFavorites(await api.listFavorites());
    } catch (error) {
      setErrorKind(getApiErrorKind(error));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadFavorites();
  }, [loadFavorites]);

  const onRefresh = useCallback(() => {
    setRefreshing(true);
    loadFavorites().finally(() => setRefreshing(false));
  }, [loadFavorites]);

  const handleUnfollow = async (favorite: FavoriteAthlete) => {
    const actionKey = `${favorite.raceId}:${favorite.participantId}`;
    if (activeActionsRef.current[actionKey]) return;
    activeActionsRef.current[actionKey] = true;
    setActiveActions((current) => ({ ...current, [actionKey]: true }));
    try {
      await api.unfollowAthlete(favorite.raceId, favorite.participantId);
      setFavorites((current) => current.filter(
        (item) => `${item.raceId}:${item.participantId}` !== actionKey,
      ));
    } catch {
      Alert.alert(t('mobile:following.unfollowFailed'));
    } finally {
      activeActionsRef.current[actionKey] = false;
      setActiveActions((current) => ({ ...current, [actionKey]: false }));
    }
  };

  if (isLoading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color="#0F766E" />
      </View>
    );
  }

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={styles.content}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
    >
      <View style={styles.header}>
        <Text style={styles.title}>{t('mobile:following.title')}</Text>
        <Text style={styles.description}>{t('mobile:following.description')}</Text>
      </View>

      {errorKind && <RequestErrorState resource="favorites" kind={errorKind} onRetry={() => void loadFavorites()} />}

      {!errorKind && favorites.length === 0 && (
        <View style={styles.emptyCard}>
          <Ionicons name="heart-outline" size={48} color="#0F766E" />
          <Text style={styles.emptyTitle}>{t('mobile:following.emptyTitle')}</Text>
          <Text style={styles.emptyText}>{t('mobile:following.emptyDescription')}</Text>
          <Link href="/(tabs)/races" asChild>
            <TouchableOpacity style={styles.primaryButton}>
              <Text style={styles.primaryButtonText}>{t('mobile:following.browseRaces')}</Text>
            </TouchableOpacity>
          </Link>
        </View>
      )}

      {favorites.map((favorite) => {
        const actionKey = `${favorite.raceId}:${favorite.participantId}`;
        const isBusy = Boolean(activeActions[actionKey]);
        return (
          <View key={actionKey} style={styles.favoriteCard}>
            <View style={styles.favoriteIcon}>
              <Ionicons name="person-outline" size={22} color="#0F766E" />
            </View>
            <View style={styles.favoriteDetails}>
              <Text style={styles.participantName}>
                {favorite.participantName || t('mobile:following.athlete')}
                {favorite.bibNumber ? ` #${favorite.bibNumber}` : ''}
              </Text>
              <Text style={styles.raceName}>
                {favorite.raceName || t('mobile:following.race')}
              </Text>
              {favorite.lastCheckpoint && (
                <Text style={styles.metadata}>
                  {t('mobile:following.lastCheckpoint', { checkpoint: favorite.lastCheckpoint })}
                </Text>
              )}
              {favorite.distanceRemaining !== undefined && (
                <Text style={styles.metadata}>
                  {t('mobile:following.distanceRemaining', { distance: formatDistanceKm(favorite.distanceRemaining) })}
                </Text>
              )}
              {favorite.status && <Text style={styles.status}>{t(statusTranslationKey(favorite.status), { defaultValue: favorite.status })}</Text>}
            </View>
            <View style={styles.favoriteActions}>
              <Link href={`/race/${favorite.raceId}`} asChild>
                <TouchableOpacity
                  style={styles.viewButton}
                  accessibilityRole="button"
                  accessibilityLabel={t('mobile:following.openRace')}
                  accessibilityHint={t('mobile:races.openHint')}
                  accessibilityState={{ disabled: false }}
                >
                  <Ionicons name="flag-outline" size={18} color="#0F766E" />
                </TouchableOpacity>
              </Link>
              <TouchableOpacity
                style={styles.removeButton}
                onPress={() => void handleUnfollow(favorite)}
                disabled={isBusy}
                accessibilityRole="button"
                accessibilityLabel={`${t('mobile:following.unfollow')} ${favorite.participantName || t('mobile:following.athlete')}`}
                accessibilityHint={t('mobile:following.unfollowHint')}
                accessibilityState={{ busy: isBusy, disabled: isBusy }}
              >
                {isBusy ? (
                  <ActivityIndicator size="small" color="#DC2626" />
                ) : (
                  <Ionicons name="heart-dislike-outline" size={18} color="#DC2626" />
                )}
              </TouchableOpacity>
            </View>
          </View>
        );
      })}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F3F4F6',
  },
  content: {
    padding: 16,
    gap: 12,
  },
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#F3F4F6',
  },
  header: {
    marginBottom: 4,
  },
  title: {
    color: '#1F2937',
    fontSize: 24,
    fontWeight: '700',
  },
  description: {
    color: '#6B7280',
    fontSize: 14,
    lineHeight: 20,
    marginTop: 6,
  },
  emptyCard: {
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    borderRadius: 16,
    padding: 28,
  },
  emptyTitle: {
    color: '#1F2937',
    fontSize: 18,
    fontWeight: '700',
    marginTop: 12,
    textAlign: 'center',
  },
  emptyText: {
    color: '#6B7280',
    fontSize: 14,
    lineHeight: 20,
    marginTop: 6,
    textAlign: 'center',
  },
  primaryButton: {
    backgroundColor: '#0F766E',
    borderRadius: 10,
    marginTop: 18,
    paddingHorizontal: 18,
    paddingVertical: 12,
  },
  primaryButtonText: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '700',
  },
  favoriteCard: {
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    borderRadius: 16,
    flexDirection: 'row',
    gap: 12,
    padding: 14,
  },
  favoriteIcon: {
    alignItems: 'center',
    backgroundColor: '#CCFBF1',
    borderRadius: 22,
    height: 44,
    justifyContent: 'center',
    width: 44,
  },
  favoriteDetails: {
    flex: 1,
    gap: 2,
  },
  participantName: {
    color: '#1F2937',
    fontSize: 16,
    fontWeight: '700',
  },
  raceName: {
    color: '#0F766E',
    fontSize: 14,
    fontWeight: '600',
  },
  metadata: {
    color: '#6B7280',
    fontSize: 12,
  },
  status: {
    alignSelf: 'flex-start',
    color: '#92400E',
    fontSize: 12,
    fontWeight: '700',
    marginTop: 2,
    textTransform: 'capitalize',
  },
  favoriteActions: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: 8,
  },
  viewButton: {
    alignItems: 'center',
    borderColor: '#99F6E4',
    borderRadius: 10,
    borderWidth: 1,
    height: 38,
    justifyContent: 'center',
    width: 38,
  },
  removeButton: {
    alignItems: 'center',
    borderColor: '#FECACA',
    borderRadius: 10,
    borderWidth: 1,
    height: 38,
    justifyContent: 'center',
    width: 38,
  },
});
