import { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Alert, AppState, FlatList, RefreshControl, StyleSheet, Text, TextInput, TouchableOpacity, View } from 'react-native';
import { router, useFocusEffect, useLocalSearchParams } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { useTranslation } from 'react-i18next';
import { useTheme } from '@/contexts/theme';
import { useAuthStore } from '@/store/auth';
import { getPreferredCapability, isOperationsRole } from '@/lib/capabilities';
import { api, getApiErrorKind, getNonEmptyRouteId, type ApiErrorKind } from '@/lib/api';
import { RequestErrorState } from '@/components/RequestErrorState';
import { formatDistanceKm, formatTime, statusTranslationKey } from '@/lib/format';
import type { PublicAthleteSummary } from '@/types';

const REFRESH_INTERVAL_MS = 30000;
const SEARCH_DEBOUNCE_MS = 400;
const LEADERBOARD_SIZE = 50;

type Tab = 'leaderboard' | 'search';

export default function RaceLiveScreen() {
  const { t } = useTranslation();
  const { colors } = useTheme();
  const { id } = useLocalSearchParams<{ id?: string }>();
  const raceId = getNonEmptyRouteId(id);
  const { user, isAuthenticated } = useAuthStore();
  const canFollow = isAuthenticated && !isOperationsRole(user?.role) && getPreferredCapability(user) === 'SPECTATOR';

  const [tab, setTab] = useState<Tab>('leaderboard');
  const [leaders, setLeaders] = useState<PublicAthleteSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [errorKind, setErrorKind] = useState<ApiErrorKind | null>(null);
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null);
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<PublicAthleteSummary[]>([]);
  const [searching, setSearching] = useState(false);
  const [searchError, setSearchError] = useState<ApiErrorKind | null>(null);
  const [followed, setFollowed] = useState<Set<string>>(new Set());
  const [busyFollow, setBusyFollow] = useState<string | null>(null);
  const [searchNonce, setSearchNonce] = useState(0);
  const searchSequence = useRef(0);

  const loadLeaderboard = useCallback(async () => {
    if (!raceId) return;
    try {
      setLeaders(await api.getRaceLeaderboard(raceId, LEADERBOARD_SIZE));
      setUpdatedAt(new Date());
      setErrorKind(null);
    } catch (error) {
      setErrorKind(getApiErrorKind(error));
    }
  }, [raceId]);

  useEffect(() => {
    setLoading(true);
    void loadLeaderboard().finally(() => setLoading(false));
  }, [loadLeaderboard]);

  useEffect(() => {
    if (!canFollow || !raceId) return;
    let active = true;
    api.listFavorites()
      .then((rows) => {
        if (active) setFollowed(new Set(rows.filter((row) => row.raceId === raceId).map((row) => row.participantId)));
      })
      .catch(() => undefined);
    return () => { active = false; };
  }, [canFollow, raceId]);

  useFocusEffect(useCallback(() => {
    let timer: ReturnType<typeof setInterval> | null = null;
    const start = () => {
      if (!timer) timer = setInterval(() => void loadLeaderboard(), REFRESH_INTERVAL_MS);
    };
    const stop = () => {
      if (timer) clearInterval(timer);
      timer = null;
    };
    start();
    const subscription = AppState.addEventListener('change', (state) => {
      if (state === 'active') {
        void loadLeaderboard();
        start();
      } else {
        stop();
      }
    });
    return () => {
      stop();
      subscription.remove();
    };
  }, [loadLeaderboard]));

  useEffect(() => {
    const term = query.trim();
    const sequence = ++searchSequence.current;
    setSearchError(null);
    if (!raceId || !term) {
      setResults([]);
      setSearching(false);
      return;
    }
    setSearching(true);
    const timer = setTimeout(() => {
      api.searchRaceAthletes(raceId, term)
        .then((rows) => { if (sequence === searchSequence.current) setResults(rows); })
        .catch((error) => { if (sequence === searchSequence.current) setSearchError(getApiErrorKind(error)); })
        .finally(() => { if (sequence === searchSequence.current) setSearching(false); });
    }, SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [query, raceId, searchNonce]);

  const onRefresh = useCallback(() => {
    setRefreshing(true);
    void loadLeaderboard().finally(() => setRefreshing(false));
  }, [loadLeaderboard]);

  const toggleFollow = async (athlete: PublicAthleteSummary) => {
    if (!raceId || busyFollow) return;
    const isFollowing = followed.has(athlete.participantId);
    setBusyFollow(athlete.participantId);
    try {
      if (isFollowing) {
        await api.unfollowAthlete(raceId, athlete.participantId);
      } else {
        await api.followAthlete(raceId, athlete.participantId);
      }
      setFollowed((current) => {
        const next = new Set(current);
        if (isFollowing) next.delete(athlete.participantId); else next.add(athlete.participantId);
        return next;
      });
    } catch {
      Alert.alert(t('mobile:raceLive.followFailedTitle'), t('mobile:raceLive.followFailed'));
    } finally {
      setBusyFollow(null);
    }
  };

  const openAthlete = (athlete: PublicAthleteSummary) => {
    if (!raceId) return;
    router.push({ pathname: '/athlete/[raceId]/[participantId]', params: { raceId, participantId: athlete.participantId } });
  };

  const renderAthlete = ({ item, index }: { item: PublicAthleteSummary; index: number }) => {
    const name = item.participantName || t('mobile:following.athlete');
    const isFollowing = followed.has(item.participantId);
    const showRank = tab === 'leaderboard';
    return (
      <TouchableOpacity style={[styles.row, { backgroundColor: colors.cardBackground }]} onPress={() => openAthlete(item)}
        accessibilityRole="button" accessibilityLabel={t('mobile:raceLive.openAthlete', { name })}>
        {showRank ? (
          <View style={[styles.rank, { backgroundColor: colors.primary }]}>
            <Text style={styles.rankText}>{item.rank ?? index + 1}</Text>
          </View>
        ) : null}
        <View style={styles.rowBody}>
          <Text style={[styles.name, { color: colors.text }]} numberOfLines={1}>
            {name}{item.bibNumber ? ` · #${item.bibNumber}` : ''}
          </Text>
          <Text style={[styles.meta, { color: colors.subText }]} numberOfLines={1}>
            {[
              item.status ? t(statusTranslationKey(item.status), { defaultValue: item.status }) : null,
              item.lastCheckpoint ? t('mobile:raceLive.lastCheckpoint', { checkpoint: item.lastCheckpoint }) : null,
            ].filter(Boolean).join(' · ')}
          </Text>
          <Text style={[styles.meta, { color: colors.subText }]} numberOfLines={1}>
            {[
              item.elapsedTime ? t('mobile:raceLive.elapsed', { time: item.elapsedTime }) : null,
              item.distanceRemaining !== null && item.distanceRemaining !== undefined
                ? t('mobile:raceLive.remaining', { distance: formatDistanceKm(item.distanceRemaining) }) : null,
            ].filter(Boolean).join(' · ')}
          </Text>
        </View>
        {canFollow ? (
          <TouchableOpacity onPress={() => void toggleFollow(item)} disabled={busyFollow === item.participantId}
            style={styles.followButton} accessibilityRole="button"
            accessibilityLabel={t(isFollowing ? 'mobile:raceLive.unfollowName' : 'mobile:raceLive.followName', { name })}
            accessibilityState={{ selected: isFollowing, busy: busyFollow === item.participantId }}>
            {busyFollow === item.participantId
              ? <ActivityIndicator size="small" color={colors.primary} />
              : <Ionicons name={isFollowing ? 'heart' : 'heart-outline'} size={22} color={colors.primary} />}
          </TouchableOpacity>
        ) : (
          <Ionicons name="chevron-forward" size={18} color={colors.subText} />
        )}
      </TouchableOpacity>
    );
  };

  if (!raceId) {
    return <RequestErrorState resource="race" kind="not_found" onRetry={() => undefined} />;
  }

  return (
    <View style={[styles.container, { backgroundColor: colors.background }]}>
      <View style={styles.tabs} accessibilityRole="tablist">
        {(['leaderboard', 'search'] as Tab[]).map((key) => (
          <TouchableOpacity key={key} onPress={() => setTab(key)} accessibilityRole="tab"
            accessibilityState={{ selected: tab === key }}
            style={[styles.tab, { borderColor: colors.border }, tab === key && { backgroundColor: colors.primary, borderColor: colors.primary }]}>
            <Text style={[styles.tabText, { color: tab === key ? '#FFFFFF' : colors.text }]}>{t(`mobile:raceLive.tabs.${key}`)}</Text>
          </TouchableOpacity>
        ))}
      </View>

      {tab === 'leaderboard' ? (
        loading ? (
          <ActivityIndicator style={styles.loading} color={colors.primary} accessibilityLabel={t('mobile:raceLive.loading')} />
        ) : (
          <FlatList
            data={leaders}
            keyExtractor={(item) => item.participantId}
            renderItem={renderAthlete}
            contentContainerStyle={styles.list}
            refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
            ListHeaderComponent={
              <View style={styles.listHeader} accessibilityLiveRegion="polite">
                <Text style={[styles.hint, { color: colors.subText }]}>{t('mobile:raceLive.leaderboardHelp')}</Text>
                {updatedAt ? (
                  <Text style={[styles.hint, { color: colors.subText }]}>
                    {t('mobile:raceLive.updatedAt', { time: formatTime(updatedAt, { hour: '2-digit', minute: '2-digit', second: '2-digit' }) })}
                  </Text>
                ) : null}
                {errorKind ? <RequestErrorState resource="race" kind={errorKind} onRetry={() => void loadLeaderboard()} compact /> : null}
              </View>
            }
            ListEmptyComponent={!errorKind
              ? <Text style={[styles.empty, { color: colors.subText }]}>{t('mobile:raceLive.noneOnCourse')}</Text>
              : null}
          />
        )
      ) : (
        <View style={styles.searchPane}>
          <TextInput
            style={[styles.input, { borderColor: colors.border, color: colors.text, backgroundColor: colors.cardBackground }]}
            value={query}
            onChangeText={setQuery}
            placeholder={t('mobile:raceLive.searchPlaceholder')}
            placeholderTextColor={colors.subText}
            accessibilityLabel={t('mobile:raceLive.searchPlaceholder')}
            autoCorrect={false}
            maxLength={50}
            returnKeyType="search"
          />
          {searching ? <ActivityIndicator color={colors.primary} /> : null}
          {searchError ? <RequestErrorState resource="race" kind={searchError} onRetry={() => setSearchNonce((value) => value + 1)} compact /> : null}
          <FlatList
            data={results}
            keyExtractor={(item) => item.participantId}
            renderItem={renderAthlete}
            contentContainerStyle={styles.list}
            keyboardShouldPersistTaps="handled"
            ListEmptyComponent={query.trim() && !searching && !searchError
              ? <Text style={[styles.empty, { color: colors.subText }]}>{t('mobile:raceLive.noMatches')}</Text>
              : <Text style={[styles.empty, { color: colors.subText }]}>{t('mobile:raceLive.searchHelp')}</Text>}
          />
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  tabs: { flexDirection: 'row', gap: 8, padding: 16, paddingBottom: 8 },
  tab: { flex: 1, borderWidth: 1, borderRadius: 20, paddingVertical: 10, alignItems: 'center' },
  tabText: { fontWeight: '700' },
  loading: { marginTop: 32 },
  list: { padding: 16, paddingTop: 8, gap: 8 },
  listHeader: { gap: 4, marginBottom: 4 },
  hint: { fontSize: 13 },
  empty: { textAlign: 'center', marginTop: 24, fontSize: 15 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 12, borderRadius: 14, padding: 12 },
  rank: { width: 34, height: 34, borderRadius: 17, alignItems: 'center', justifyContent: 'center' },
  rankText: { color: '#FFFFFF', fontWeight: '800' },
  rowBody: { flex: 1, gap: 2 },
  name: { fontSize: 15, fontWeight: '700' },
  meta: { fontSize: 13 },
  followButton: { padding: 6 },
  searchPane: { flex: 1, paddingHorizontal: 16, gap: 8 },
  input: { borderWidth: 1, borderRadius: 12, padding: 14, fontSize: 16 },
});
