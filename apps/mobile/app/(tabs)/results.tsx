import { View, Text, ScrollView, StyleSheet, TouchableOpacity, ActivityIndicator, RefreshControl } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { api } from '@/lib/api';

type ResultRow = {
  id: string;
  race: string;
  date: string;
  time: string;
  position?: number;
  category: string;
  distance: number;
  averagePace?: string;
  raceDateTs: number;
};

function toDateLabel(value?: string) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '-';
  return date.toLocaleDateString();
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
  const [results, setResults] = useState<ResultRow[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [resultPage, setResultPage] = useState(0);
  const [hasMoreResults, setHasMoreResults] = useState(false);
  const [isLoadingMore, setIsLoadingMore] = useState(false);

  const loadResults = useCallback(async (page = 0, append = false) => {
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
          race: race?.name || 'Race',
          date: toDateLabel(raceDate),
          time: result.totalTime || result.chipTime || result.gunTime || '-',
          position: result.overallPosition,
          category: result.category || race?.raceType || 'Category',
          distance: Number(race?.distanceKm || 0),
          averagePace: result.averagePace,
          raceDateTs: raceDate ? new Date(raceDate).getTime() : 0,
        };
      })
      .sort((a, b) => b.raceDateTs - a.raceDateTs);

    setResults((current) => append ? [...current, ...mapped] : mapped);
    setResultPage(resultPageData.page);
    setHasMoreResults(resultPageData.page + 1 < resultPageData.totalPages);
    } finally {
      if (append) setIsLoadingMore(false);
    }
  }, []);

  useEffect(() => {
    loadResults().finally(() => setIsLoading(false));
  }, [loadResults]);

  const onRefresh = useCallback(() => {
    setRefreshing(true);
    loadResults(0).finally(() => setRefreshing(false));
  }, [loadResults]);

  const stats = useMemo(() => {
    const totalRaces = results.length;
    const totalDistance = results.reduce((sum, item) => sum + (item.distance || 0), 0);

    const marathonResults = results.filter((item) => item.distance >= 42 && item.time !== '-');
    const bestMarathon = marathonResults
      .map((item) => item.time)
      .sort((a, b) => {
        const left = parseDurationToSeconds(a);
        const right = parseDurationToSeconds(b);
        if (left === null) return 1;
        if (right === null) return -1;
        return left - right;
      })[0] || '-';

    const paceFromResults = results.find((item) => !!item.averagePace)?.averagePace;
    if (paceFromResults) {
      return {
        totalRaces,
        totalDistance,
        bestMarathon,
        averagePace: paceFromResults,
      };
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

    return {
      totalRaces,
      totalDistance,
      bestMarathon,
      averagePace,
    };
  }, [results]);

  const achievements = useMemo(
    () => [
      {
        id: 'first-finish',
        icon: 'trophy',
        title: 'First Finish',
        description: 'Completed your first race',
        date: results.length ? 'Unlocked' : 'Locked',
        unlocked: results.length > 0,
      },
      {
        id: 'marathon-finish',
        icon: 'medal',
        title: 'Marathon Finisher',
        description: 'Finished a marathon distance race',
        date: results.some((r) => r.distance >= 42) ? 'Unlocked' : 'Locked',
        unlocked: results.some((r) => r.distance >= 42),
      },
      {
        id: 'ten-races',
        icon: 'star',
        title: '10 Races',
        description: 'Completed ten races',
        date: results.length >= 10 ? 'Unlocked' : 'Locked',
        unlocked: results.length >= 10,
      },
    ],
    [results],
  );

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
      showsVerticalScrollIndicator={false}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
    >
      <View style={styles.statsGrid}>
        <View style={styles.statCard}>
          <Ionicons name="flag" size={24} color="#0F766E" />
          <Text style={styles.statValue}>{stats.totalRaces}</Text>
          <Text style={styles.statLabel}>Races</Text>
        </View>
        <View style={styles.statCard}>
          <Ionicons name="walk" size={24} color="#0F766E" />
          <Text style={styles.statValue}>{stats.totalDistance.toFixed(1)}</Text>
          <Text style={styles.statLabel}>Total KM</Text>
        </View>
        <View style={styles.statCard}>
          <Ionicons name="trophy" size={24} color="#0F766E" />
          <Text style={styles.statValue}>{stats.bestMarathon}</Text>
          <Text style={styles.statLabel}>Best Marathon</Text>
        </View>
        <View style={styles.statCard}>
          <Ionicons name="speedometer" size={24} color="#0F766E" />
          <Text style={styles.statValue}>{stats.averagePace}</Text>
          <Text style={styles.statLabel}>Avg Pace</Text>
        </View>
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Achievements</Text>
        <ScrollView horizontal showsHorizontalScrollIndicator={false}>
          {achievements.map((achievement) => (
            <View key={achievement.id} style={styles.achievementCard}>
              <View style={[styles.achievementIcon, { backgroundColor: achievement.unlocked ? '#FEF3C7' : '#F3F4F6' }]}>
                <Ionicons name={achievement.icon as any} size={28} color={achievement.unlocked ? '#FCD34D' : '#9CA3AF'} />
              </View>
              <Text style={styles.achievementTitle}>{achievement.title}</Text>
              <Text style={styles.achievementDesc}>{achievement.description}</Text>
              <Text style={styles.achievementDate}>{achievement.date}</Text>
            </View>
          ))}
        </ScrollView>
      </View>

      <View style={styles.section}>
        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>Recent Results</Text>
          <TouchableOpacity>
            <Text style={styles.seeAllText}>See All</Text>
          </TouchableOpacity>
        </View>
        {results.length ? (
          results.map((result) => (
            <View key={result.id} style={styles.resultCard}>
              <View style={styles.resultMain}>
                <Text style={styles.resultRace}>{result.race}</Text>
                <Text style={styles.resultDate}>{result.date}</Text>
                <View style={styles.resultCategory}>
                  <Text style={styles.resultCategoryText}>{result.category}</Text>
                </View>
              </View>
              <View style={styles.resultStats}>
                <View style={styles.resultStatItem}>
                  <Text style={styles.resultStatValue}>{result.time}</Text>
                  <Text style={styles.resultStatLabel}>Time</Text>
                </View>
                <View style={styles.resultStatItem}>
                  <Text style={styles.resultStatValue}>{result.position ? `#${result.position}` : '-'}</Text>
                  <Text style={styles.resultStatLabel}>Position</Text>
                </View>
              </View>
            </View>
          ))
        ) : (
          <View style={styles.emptyState}>
            <Text style={styles.emptyText}>No results available yet</Text>
          </View>
        )}
        {hasMoreResults && (
          <TouchableOpacity disabled={isLoadingMore} onPress={() => void loadResults(resultPage + 1, true)}>
            <Text style={styles.seeAllText}>{isLoadingMore ? 'Loading more results…' : 'Load more results'}</Text>
          </TouchableOpacity>
        )}
      </View>

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
});
