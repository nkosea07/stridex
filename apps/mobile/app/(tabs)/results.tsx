import { View, Text, ScrollView, StyleSheet, TouchableOpacity, ActivityIndicator, RefreshControl, Modal, TextInput, Alert } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import * as WebBrowser from 'expo-web-browser';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { api, certificateUrl, getApiErrorKind, type ApiErrorKind } from '@/lib/api';
import { CapabilityGate } from '@/components/CapabilityGate';
import { RequestErrorState } from '@/components/RequestErrorState';
import { formatDate, formatDateTime, formatDistanceKm } from '@/lib/format';
import type { AthleteHistoryEntry, PersonalBest, ResultQuery } from '@/types';

type ResultRow = {
  id: string;
  raceId: string;
  participantId: string;
  race: string;
  date: string;
  time: string;
  position?: number;
  category: string;
  distance: number;
  averagePace?: string;
  raceDateTs: number;
  official: boolean;
  certificateToken?: string | null;
};

const QUERY_MIN_LENGTH = 10;
const QUERY_MAX_LENGTH = 1000;

function toDateLabel(value?: string) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '-';
  return formatDate(date);
}

function parseDurationToSeconds(value?: string): number | null {
  if (!value) return null;
  const parts = value.split(':').map((part) => Number(part));
  if (parts.some((part) => Number.isNaN(part))) return null;
  if (parts.length === 3) return (parts[0] * 3600) + (parts[1] * 60) + parts[2];
  if (parts.length === 2) return (parts[0] * 60) + parts[1];
  return null;
}

function formatPace(secondsPerKm: number): string {
  const minutes = Math.floor(secondsPerKm / 60);
  const seconds = Math.round(secondsPerKm % 60);
  return `${minutes}:${seconds.toString().padStart(2, '0')}/km`;
}

export default function ResultsScreen() {
  return (
    <CapabilityGate capability="ATHLETE">
      <ResultsScreenContent />
    </CapabilityGate>
  );
}

function ResultsScreenContent() {
  const { t } = useTranslation();
  const [results, setResults] = useState<ResultRow[]>([]);
  const [queries, setQueries] = useState<ResultQuery[]>([]);
  const [personalBests, setPersonalBests] = useState<PersonalBest[]>([]);
  const [history, setHistory] = useState<AthleteHistoryEntry[] | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [resultPage, setResultPage] = useState(0);
  const [hasMoreResults, setHasMoreResults] = useState(false);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [errorKind, setErrorKind] = useState<ApiErrorKind | null>(null);
  const [queryTarget, setQueryTarget] = useState<ResultRow | null>(null);
  const [queryMessage, setQueryMessage] = useState('');
  const [submittingQuery, setSubmittingQuery] = useState(false);

  const loadQueries = useCallback(async () => {
    const [queryRows, bests, historyRows] = await Promise.all([
      api.getMyResultQueries().catch(() => [] as ResultQuery[]),
      api.getMyPersonalBests().catch(() => [] as PersonalBest[]),
      api.getMyHistory().catch(() => null),
    ]);
    setQueries(queryRows);
    setPersonalBests(bests);
    setHistory(historyRows);
  }, []);

  const loadResults = useCallback(async (page = 0, append = false) => {
    setErrorKind(null);
    if (append) setIsLoadingMore(true);
    try {
      const [resultPageData, races] = await Promise.all([api.getMyResultsPage(page, 50), api.getRaces()]);
      const raceMap = new Map((races as any[]).map((race) => [String(race.id), race]));

      const mapped = resultPageData.items
        .map((result) => {
          const race = raceMap.get(String(result.raceId));
          const raceDate = race?.raceDate;
          return {
            id: String(result.id || `${result.raceId}-${result.participantId}`),
            raceId: String(result.raceId),
            participantId: String(result.participantId),
            race: race?.name || result.raceName || t('mobile:results.unknownRace'),
            date: toDateLabel(raceDate),
            time: result.totalTime || result.chipTime || result.gunTime || '-',
            position: result.overallPosition,
            category: result.category || race?.raceType || '-',
            distance: Number(race?.distanceKm || 0),
            averagePace: result.averagePace,
            raceDateTs: raceDate ? new Date(raceDate).getTime() : 0,
            official: Boolean(result.official),
            certificateToken: result.certificateToken ?? null,
          };
        })
        .sort((a, b) => b.raceDateTs - a.raceDateTs);

      setResults((current) => append ? [...current, ...mapped] : mapped);
      setResultPage(resultPageData.page);
      setHasMoreResults(resultPageData.page + 1 < resultPageData.totalPages);
    } catch (error) {
      setErrorKind(getApiErrorKind(error));
    } finally {
      if (append) setIsLoadingMore(false);
    }
  }, [t]);

  const reloadResults = useCallback(async () => {
    setIsLoading(true);
    try {
      await Promise.all([loadResults(0), loadQueries()]);
    } finally {
      setIsLoading(false);
    }
  }, [loadResults, loadQueries]);

  useEffect(() => {
    void reloadResults();
  }, [reloadResults]);

  const onRefresh = useCallback(() => {
    setRefreshing(true);
    Promise.all([loadResults(0), loadQueries()]).finally(() => setRefreshing(false));
  }, [loadResults, loadQueries]);

  const openCertificate = async (result: ResultRow) => {
    const url = certificateUrl(result);
    if (!url) return;
    try {
      await WebBrowser.openBrowserAsync(url);
    } catch {
      Alert.alert(t('mobile:results.certificateFailedTitle'), t('mobile:results.certificateFailed'));
    }
  };

  const submitQuery = async () => {
    if (!queryTarget) return;
    const message = queryMessage.trim();
    if (message.length < QUERY_MIN_LENGTH) {
      Alert.alert(t('mobile:results.query.title'), t('mobile:results.query.tooShort', { count: QUERY_MIN_LENGTH }));
      return;
    }
    setSubmittingQuery(true);
    try {
      await api.submitResultQuery(queryTarget.raceId, message);
      setQueryTarget(null);
      setQueryMessage('');
      Alert.alert(t('mobile:results.query.title'), t('mobile:results.query.submitted'));
      await loadQueries();
    } catch (error: any) {
      const key = error?.code === 'RESULT_QUERY_LIMIT' || error?.code === 'RESULTS_CERTIFIED'
        ? `mobile:results.query.errors.${error.code}`
        : 'mobile:results.query.failed';
      Alert.alert(t('mobile:results.query.title'), t(key));
    } finally {
      setSubmittingQuery(false);
    }
  };

  const stats = useMemo(() => {
    const counted = history ? history.filter((entry) => !entry.disqualified) : null;
    const totalRaces = counted ? counted.length : results.length;
    const totalDistance = counted
      ? counted.reduce((sum, entry) => sum + Number(entry.distanceKm ?? 0), 0)
      : results.reduce((sum, item) => sum + (item.distance || 0), 0);
    const bestMarathon = personalBests.find((best) => best.distanceKm >= 42 && best.distanceKm <= 42.5)?.chipTime || '-';

    const paceFromResults = results.find((item) => !!item.averagePace)?.averagePace;
    if (paceFromResults) {
      return { totalRaces, totalDistance, bestMarathon, averagePace: paceFromResults };
    }

    const computedPaces = results
      .map((item) => {
        const seconds = parseDurationToSeconds(item.time);
        if (!seconds || !item.distance) return null;
        return seconds / item.distance;
      })
      .filter((value): value is number => value !== null);

    const averagePace = computedPaces.length
      ? formatPace(computedPaces.reduce((sum, value) => sum + value, 0) / computedPaces.length)
      : '-';

    return { totalRaces, totalDistance, bestMarathon, averagePace };
  }, [results, history, personalBests]);

  const achievements = useMemo(
    () => [
      { id: 'firstFinish', icon: 'trophy', unlocked: stats.totalRaces > 0 },
      { id: 'marathonFinisher', icon: 'medal', unlocked: personalBests.some((best) => best.distanceKm >= 42) || results.some((r) => r.distance >= 42) },
      { id: 'tenRaces', icon: 'star', unlocked: stats.totalRaces >= 10 },
    ],
    [results, personalBests, stats.totalRaces],
  );

  if (isLoading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color="#0F766E" accessibilityLabel={t('mobile:results.loading')} />
      </View>
    );
  }

  if (errorKind && results.length === 0) {
    return <RequestErrorState resource="results" kind={errorKind} onRetry={() => void reloadResults()} />;
  }

  return (
    <ScrollView
      style={styles.container}
      showsVerticalScrollIndicator={false}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
    >
      {errorKind && <RequestErrorState resource="results" kind={errorKind} onRetry={() => void reloadResults()} compact />}
      <View style={styles.statsGrid}>
        <View style={styles.statCard}>
          <Ionicons name="flag" size={24} color="#0F766E" />
          <Text style={styles.statValue}>{stats.totalRaces}</Text>
          <Text style={styles.statLabel}>{t('mobile:results.races')}</Text>
        </View>
        <View style={styles.statCard}>
          <Ionicons name="walk" size={24} color="#0F766E" />
          <Text style={styles.statValue}>{formatDistanceKm(stats.totalDistance)}</Text>
          <Text style={styles.statLabel}>{t('mobile:results.totalDistance')}</Text>
        </View>
        <View style={styles.statCard}>
          <Ionicons name="trophy" size={24} color="#0F766E" />
          <Text style={styles.statValue}>{stats.bestMarathon}</Text>
          <Text style={styles.statLabel}>{t('mobile:results.bestMarathon')}</Text>
        </View>
        <View style={styles.statCard}>
          <Ionicons name="speedometer" size={24} color="#0F766E" />
          <Text style={styles.statValue}>{stats.averagePace}</Text>
          <Text style={styles.statLabel}>{t('mobile:results.averagePace')}</Text>
        </View>
      </View>

      {personalBests.length > 0 && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle} accessibilityRole="header">{t('mobile:results.personalBests')}</Text>
          {personalBests.map((best) => (
            <View key={`${best.distanceKm}-${best.raceId ?? ''}`} style={styles.queryCard}>
              <Text style={styles.queryStatus}>{formatDistanceKm(best.distanceKm)}</Text>
              <Text style={styles.resultRace}>{best.chipTime || '-'}</Text>
              {best.raceName ? <Text style={styles.resultDate}>{best.raceName}</Text> : null}
            </View>
          ))}
        </View>
      )}

      <View style={styles.section}>
        <Text style={styles.sectionTitle} accessibilityRole="header">{t('mobile:results.achievements')}</Text>
        <ScrollView horizontal showsHorizontalScrollIndicator={false}>
          {achievements.map((achievement) => (
            <View key={achievement.id} style={styles.achievementCard}>
              <View style={[styles.achievementIcon, { backgroundColor: achievement.unlocked ? '#FEF3C7' : '#F3F4F6' }]}>
                <Ionicons name={achievement.icon as any} size={28} color={achievement.unlocked ? '#FCD34D' : '#9CA3AF'} />
              </View>
              <Text style={styles.achievementTitle}>{t(`mobile:results.achievement.${achievement.id}.title`)}</Text>
              <Text style={styles.achievementDesc}>{t(`mobile:results.achievement.${achievement.id}.description`)}</Text>
              <Text style={styles.achievementDate}>
                {achievement.unlocked ? t('mobile:results.unlocked') : t('mobile:results.locked')}
              </Text>
            </View>
          ))}
        </ScrollView>
      </View>

      <View style={styles.section}>
        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle} accessibilityRole="header">{t('mobile:results.recentResults')}</Text>
        </View>
        {results.length ? (
          results.map((result) => (
            <View key={result.id} style={styles.resultCard}>
              <View style={styles.resultTop}>
                <View style={styles.resultMain}>
                  <Text style={styles.resultRace}>{result.race}</Text>
                  <Text style={styles.resultDate}>{result.date}</Text>
                  <View style={styles.resultBadges}>
                    <View style={styles.resultCategory}>
                      <Text style={styles.resultCategoryText}>{result.category}</Text>
                    </View>
                    <View style={[styles.resultCategory, result.official ? styles.officialBadge : styles.provisionalBadge]}>
                      <Text style={styles.resultCategoryText}>
                        {result.official ? t('mobile:results.official') : t('mobile:results.provisional')}
                      </Text>
                    </View>
                  </View>
                </View>
                <View style={styles.resultStats}>
                  <View style={styles.resultStatItem}>
                    <Text style={styles.resultStatValue}>{result.time}</Text>
                    <Text style={styles.resultStatLabel}>{t('mobile:results.time')}</Text>
                  </View>
                  <View style={styles.resultStatItem}>
                    <Text style={styles.resultStatValue}>{result.position ? `#${result.position}` : '-'}</Text>
                    <Text style={styles.resultStatLabel}>{t('mobile:results.position')}</Text>
                  </View>
                </View>
              </View>
              <View style={styles.resultActions}>
                {result.certificateToken ? (
                  <TouchableOpacity style={styles.resultAction} onPress={() => void openCertificate(result)}
                    accessibilityRole="button" accessibilityLabel={t('mobile:results.certificate')}>
                    <Ionicons name="ribbon-outline" size={16} color="#0F766E" />
                    <Text style={styles.resultActionText}>{t('mobile:results.certificate')}</Text>
                  </TouchableOpacity>
                ) : null}
                <TouchableOpacity style={styles.resultAction} onPress={() => { setQueryTarget(result); setQueryMessage(''); }}
                  accessibilityRole="button" accessibilityLabel={t('mobile:results.query.open')}>
                  <Ionicons name="help-circle-outline" size={16} color="#0F766E" />
                  <Text style={styles.resultActionText}>{t('mobile:results.query.open')}</Text>
                </TouchableOpacity>
              </View>
            </View>
          ))
        ) : (
          <View style={styles.emptyState}>
            <Text style={styles.emptyText}>{t('mobile:results.empty')}</Text>
          </View>
        )}
        {hasMoreResults && (
          <TouchableOpacity
            disabled={isLoadingMore}
            onPress={() => void loadResults(resultPage + 1, true)}
            accessibilityRole="button"
            accessibilityLabel={t('mobile:results.loadMore')}
            accessibilityState={{ busy: isLoadingMore, disabled: isLoadingMore }}
          >
            <Text style={styles.seeAllText}>{isLoadingMore ? t('mobile:results.loadingMore') : t('mobile:results.loadMore')}</Text>
          </TouchableOpacity>
        )}
      </View>

      {queries.length > 0 && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle} accessibilityRole="header">{t('mobile:results.query.listTitle')}</Text>
          {queries.map((query) => (
            <View key={query.id} style={styles.queryCard}>
              <Text style={styles.queryStatus}>{t(`mobile:results.query.status.${query.status}`, { defaultValue: query.status })}</Text>
              {query.createdAt ? <Text style={styles.resultDate}>{formatDateTime(query.createdAt)}</Text> : null}
              <Text style={styles.queryMessage}>{query.message}</Text>
              {query.resolutionNote ? (
                <Text style={styles.queryResolution}>{t('mobile:results.query.answer', { note: query.resolutionNote })}</Text>
              ) : null}
            </View>
          ))}
        </View>
      )}

      <Modal visible={queryTarget !== null} transparent animationType="slide" onRequestClose={() => setQueryTarget(null)}>
        <View style={styles.modalBackdrop}>
          <View style={styles.modalCard} accessibilityViewIsModal>
            <Text style={styles.sectionTitle} accessibilityRole="header">{t('mobile:results.query.title')}</Text>
            <Text style={styles.resultDate}>{queryTarget?.race}</Text>
            <Text style={styles.queryHelp}>{t('mobile:results.query.help')}</Text>
            <TextInput
              style={styles.queryInput}
              value={queryMessage}
              onChangeText={setQueryMessage}
              multiline
              maxLength={QUERY_MAX_LENGTH}
              placeholder={t('mobile:results.query.placeholder')}
              placeholderTextColor="#9CA3AF"
              accessibilityLabel={t('mobile:results.query.placeholder')}
            />
            <View style={styles.modalActions}>
              <TouchableOpacity onPress={() => setQueryTarget(null)} disabled={submittingQuery}
                accessibilityRole="button" style={styles.modalSecondary}>
                <Text style={styles.resultActionText}>{t('mobile:results.query.cancel')}</Text>
              </TouchableOpacity>
              <TouchableOpacity onPress={() => void submitQuery()} disabled={submittingQuery}
                accessibilityRole="button" accessibilityState={{ busy: submittingQuery, disabled: submittingQuery }}
                style={styles.modalPrimary}>
                <Text style={styles.modalPrimaryText}>
                  {submittingQuery ? t('mobile:results.query.sending') : t('mobile:results.query.send')}
                </Text>
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
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#F3F4F6',
  },
  statsGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    padding: 16,
    gap: 12,
  },
  statCard: {
    width: '47%',
    backgroundColor: '#FFFFFF',
    borderRadius: 16,
    padding: 16,
    alignItems: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 8,
    elevation: 3,
  },
  statValue: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#1F2937',
    marginTop: 8,
  },
  statLabel: {
    fontSize: 14,
    color: '#6B7280',
    marginTop: 4,
  },
  section: {
    padding: 16,
    paddingTop: 8,
  },
  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#1F2937',
    marginBottom: 12,
  },
  seeAllText: {
    fontSize: 14,
    fontWeight: '500',
    color: '#0F766E',
  },
  achievementCard: {
    backgroundColor: '#FFFFFF',
    borderRadius: 16,
    padding: 16,
    marginRight: 12,
    width: 160,
    alignItems: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 8,
    elevation: 3,
  },
  achievementIcon: {
    width: 56,
    height: 56,
    borderRadius: 28,
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: 12,
  },
  achievementTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: '#1F2937',
    textAlign: 'center',
  },
  achievementDesc: {
    fontSize: 12,
    color: '#6B7280',
    textAlign: 'center',
    marginTop: 4,
  },
  achievementDate: {
    fontSize: 12,
    color: '#9CA3AF',
    marginTop: 8,
  },
  resultCard: {
    backgroundColor: '#FFFFFF',
    borderRadius: 16,
    padding: 16,
    marginBottom: 12,
    flexDirection: 'row',
    justifyContent: 'space-between',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 4,
    elevation: 2,
  },
  resultMain: {
    flex: 1,
  },
  resultRace: {
    fontSize: 16,
    fontWeight: '600',
    color: '#1F2937',
  },
  resultDate: {
    fontSize: 14,
    color: '#6B7280',
    marginTop: 4,
  },
  resultCategory: {
    alignSelf: 'flex-start',
    backgroundColor: '#F0FDF4',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 6,
    marginTop: 8,
  },
  resultCategoryText: {
    fontSize: 12,
    fontWeight: '500',
    color: '#0F766E',
  },
  resultStats: {
    flexDirection: 'row',
    gap: 16,
  },
  resultStatItem: {
    alignItems: 'center',
  },
  resultStatValue: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#0F766E',
  },
  resultStatLabel: {
    fontSize: 12,
    color: '#6B7280',
    marginTop: 2,
  },
  emptyState: {
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    padding: 16,
    alignItems: 'center',
  },
  emptyText: {
    color: '#6B7280',
    fontSize: 15,
  },
  bottomSpacing: {
    height: 24,
  },
  resultTop: {
    flexDirection: 'row',
  },
  resultBadges: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 6,
  },
  officialBadge: {
    backgroundColor: '#DCFCE7',
  },
  provisionalBadge: {
    backgroundColor: '#FEF3C7',
  },
  resultActions: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 16,
    marginTop: 12,
    borderTopWidth: 1,
    borderTopColor: '#F3F4F6',
    paddingTop: 10,
  },
  resultAction: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    minHeight: 32,
  },
  resultActionText: {
    color: '#0F766E',
    fontSize: 14,
    fontWeight: '600',
  },
  queryCard: {
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    padding: 14,
    marginBottom: 10,
  },
  queryStatus: {
    fontSize: 13,
    fontWeight: '700',
    color: '#0F766E',
  },
  queryMessage: {
    fontSize: 14,
    color: '#374151',
    marginTop: 6,
  },
  queryResolution: {
    fontSize: 14,
    color: '#111827',
    marginTop: 8,
    fontStyle: 'italic',
  },
  queryHelp: {
    fontSize: 13,
    color: '#6B7280',
    marginVertical: 8,
  },
  queryInput: {
    minHeight: 120,
    borderWidth: 1,
    borderColor: '#D1D5DB',
    borderRadius: 10,
    padding: 12,
    textAlignVertical: 'top',
    color: '#111827',
  },
  modalBackdrop: {
    flex: 1,
    justifyContent: 'flex-end',
    backgroundColor: 'rgba(0,0,0,0.4)',
  },
  modalCard: {
    backgroundColor: '#FFFFFF',
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    padding: 20,
  },
  modalActions: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    gap: 12,
    marginTop: 16,
  },
  modalSecondary: {
    paddingVertical: 12,
    paddingHorizontal: 16,
  },
  modalPrimary: {
    backgroundColor: '#0F766E',
    borderRadius: 10,
    paddingVertical: 12,
    paddingHorizontal: 20,
  },
  modalPrimaryText: {
    color: '#FFFFFF',
    fontWeight: '600',
  },
});
