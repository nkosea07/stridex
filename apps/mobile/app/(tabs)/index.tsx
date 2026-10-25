import { View, Text, ScrollView, StyleSheet, TouchableOpacity, Image, RefreshControl, ActivityIndicator } from 'react-native';
import { Link } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { useState, useCallback, useEffect } from 'react';
import { useAuthStore } from '@/store/auth';
import { api } from '@/lib/api';
import { getPreferredCapability, hasAnyCapability, hasCapability, isOperationsRole } from '@/lib/capabilities';
import { UnsupportedMobileState } from '@/components/CapabilityGate';
import { useTranslation } from 'react-i18next';
import { formatDate, formatDistanceKm, formatNumber } from '@/lib/format';

type UpcomingRace = {
  id: string;
  name: string;
  date: string;
  location: string;
  distance: number;
  daysUntil: number;
  bibNumber?: string;
  category: string;
  bannerImage?: string;
};

type RecentRaceResult = {
  id: string;
  name: string;
  date: string;
  position?: number;
  time: string;
};

function toDateLabel(value?: string) {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '-';
  return formatDate(date);
}

function toDaysUntil(value?: string) {
  if (!value) return 0;
  const now = new Date();
  const target = new Date(value);
  if (Number.isNaN(target.getTime())) return 0;

  const nowStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const targetStart = new Date(target.getFullYear(), target.getMonth(), target.getDate()).getTime();
  return Math.max(0, Math.ceil((targetStart - nowStart) / (1000 * 60 * 60 * 24)));
}

const DEFAULT_BANNER = 'https://images.unsplash.com/photo-1513593771513-7b58b6c4af38?w=800';

export default function HomeScreen() {
  const { user, isAuthenticated } = useAuthStore();
  const { t } = useTranslation();
  const isAthlete = hasCapability(user, 'ATHLETE');
  const isSpectator = hasCapability(user, 'SPECTATOR');
  const isOperationsUser = isOperationsRole(user?.role);
  const activeCapability = getPreferredCapability(user);
  const [refreshing, setRefreshing] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [upcomingRace, setUpcomingRace] = useState<UpcomingRace | null>(null);
  const [recentRaces, setRecentRaces] = useState<RecentRaceResult[]>([]);

  const loadHomeData = useCallback(async () => {
    if (!isAthlete || activeCapability !== 'ATHLETE') {
      setUpcomingRace(null);
      setRecentRaces([]);
      return;
    }

    const [registrations, races, myResults] = await Promise.all([
      api.getMyRaces(),
      api.getRaces(),
      api.getMyResults(),
    ]);

    const raceMap = new Map((races as any[]).map((race) => [String(race.id), race]));

    const upcomingRegistration = (registrations as any[])
      .filter((reg) => {
        const status = String(reg.status || '').toLowerCase();
        return status === 'pending' || status === 'confirmed';
      })
      .map((reg) => ({
        reg,
        race: raceMap.get(String(reg.raceId)),
      }))
      .filter((entry) => entry.race)
      .sort((a, b) => {
        const left = new Date(a.race.raceDate || 0).getTime();
        const right = new Date(b.race.raceDate || 0).getTime();
        return left - right;
      })[0];

    if (upcomingRegistration?.race) {
      const race = upcomingRegistration.race;
      setUpcomingRace({
        id: String(race.id),
        name: race.name,
        date: toDateLabel(race.raceDate),
        location: [race.city, race.country].filter(Boolean).join(', '),
        distance: Number(race.distanceKm || 0),
        daysUntil: toDaysUntil(race.raceDate),
        bibNumber: upcomingRegistration.reg.bibNumber,
        category: race.raceType || 'Race',
        bannerImage: race.bannerImageUrl || DEFAULT_BANNER,
      });
    } else {
      setUpcomingRace(null);
    }

    const mappedResults = (myResults as any[])
      .map((result) => {
        const race = raceMap.get(String(result.raceId));
        return {
          id: String(result.id || `${result.raceId}-${result.participantId}`),
          name: race?.name || 'Race',
          date: toDateLabel(race?.raceDate),
          position: result.overallPosition,
          time: result.totalTime || result.chipTime || result.gunTime || '-',
          raceDateTs: race?.raceDate ? new Date(race.raceDate).getTime() : 0,
        };
      })
      .sort((a, b) => b.raceDateTs - a.raceDateTs)
      .slice(0, 4)
      .map(({ raceDateTs, ...rest }) => rest);

    setRecentRaces(mappedResults);
  }, [activeCapability, isAthlete]);

  const onRefresh = useCallback(() => {
    setRefreshing(true);
    loadHomeData().finally(() => setRefreshing(false));
  }, [loadHomeData]);

  useEffect(() => {
    if (!isAuthenticated || isOperationsUser || !hasAnyCapability(user, ['ATHLETE', 'SPECTATOR'])) {
      setIsLoading(false);
      return;
    }

    setIsLoading(true);
    loadHomeData().finally(() => setIsLoading(false));
  }, [isAuthenticated, isOperationsUser, loadHomeData, user]);

  if (!isAuthenticated) {
    return (
      <View style={styles.authContainer}>
        <Ionicons name="walk" size={80} color="#0F766E" />
        <Text style={styles.authTitle}>Race Tracker</Text>
        <Text style={styles.authSubtitle}>Track your races in real-time</Text>
        <Link href="/(auth)/login" asChild>
          <TouchableOpacity style={styles.primaryButton}>
            <Text style={styles.primaryButtonText}>Sign In</Text>
          </TouchableOpacity>
        </Link>
        <Link href="/(auth)/register" asChild>
          <TouchableOpacity style={styles.secondaryButton}>
            <Text style={styles.secondaryButtonText}>Create Account</Text>
          </TouchableOpacity>
        </Link>
      </View>
    );
  }

  if (isOperationsUser) {
    return (
      <View style={styles.authContainer}>
        <Ionicons name="shield-checkmark-outline" size={80} color="#0F766E" />
        <Text style={styles.authTitle}>{t('mobile:operations.title')}</Text>
        <Text style={styles.authSubtitle}>{t('mobile:operations.description')}</Text>
        <Link href="/operations" asChild>
          <TouchableOpacity style={styles.primaryButton}>
            <Text style={styles.primaryButtonText}>{t('mobile:operations.record')}</Text>
          </TouchableOpacity>
        </Link>
        <Link href="/(tabs)/races" asChild>
          <TouchableOpacity style={styles.secondaryButton}>
            <Text style={styles.secondaryButtonText}>{t('mobile:home.browseRaces')}</Text>
          </TouchableOpacity>
        </Link>
      </View>
    );
  }

  if (!hasAnyCapability(user, ['ATHLETE', 'SPECTATOR'])) {
    return <UnsupportedMobileState />;
  }

  if (isLoading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color="#0F766E" />
      </View>
    );
  }

  if (isSpectator && activeCapability === 'SPECTATOR') {
    return (
      <View style={styles.authContainer}>
        <Ionicons name="eye-outline" size={80} color="#0F766E" />
        <Text style={styles.authTitle}>{t('mobile:home.spectatorTitle')}</Text>
        <Text style={styles.authSubtitle}>{t('mobile:home.spectatorDescription')}</Text>
        <Link href="/(tabs)/races" asChild>
          <TouchableOpacity style={styles.primaryButton}>
            <Text style={styles.primaryButtonText}>{t('mobile:home.browseRaces')}</Text>
          </TouchableOpacity>
        </Link>
        <Link href="/(tabs)/followed" asChild>
          <TouchableOpacity style={styles.secondaryButton}>
            <Text style={styles.secondaryButtonText}>{t('mobile:home.viewFollowed')}</Text>
          </TouchableOpacity>
        </Link>
      </View>
    );
  }

  return (
    <ScrollView
      style={styles.container}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
    >
      <View style={styles.welcomeSection}>
        <Text style={styles.welcomeText}>
          Welcome back, {user?.firstName || 'Athlete'}!
        </Text>
        <Text style={styles.subtitle}>Ready for your next challenge?</Text>
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Your Next Race</Text>
        {upcomingRace ? (
          <Link href={`/race/${upcomingRace.id}`} asChild>
            <TouchableOpacity style={styles.upcomingCard}>
              <Image
                source={{ uri: upcomingRace.bannerImage || DEFAULT_BANNER }}
                style={styles.raceBanner}
              />
              <View style={styles.raceOverlay}>
                <View style={styles.daysCountdown}>
                  <Text style={styles.daysNumber}>{formatNumber(upcomingRace.daysUntil)}</Text>
                  <Text style={styles.daysLabel}>days to go</Text>
                </View>
              </View>
              <View style={styles.raceInfo}>
                <Text style={styles.raceName}>{upcomingRace.name}</Text>
                <View style={styles.raceDetails}>
                  <View style={styles.detailRow}>
                    <Ionicons name="calendar-outline" size={16} color="#6B7280" />
                    <Text style={styles.detailText}>{upcomingRace.date}</Text>
                  </View>
                  <View style={styles.detailRow}>
                    <Ionicons name="location-outline" size={16} color="#6B7280" />
                    <Text style={styles.detailText}>{upcomingRace.location || '-'}</Text>
                  </View>
                  <View style={styles.detailRow}>
                    <Ionicons name="walk-outline" size={16} color="#6B7280" />
                    <Text style={styles.detailText}>{formatDistanceKm(upcomingRace.distance)} - {upcomingRace.category}</Text>
                  </View>
                </View>
                <View style={styles.bibContainer}>
                  <Text style={styles.bibLabel}>Your Bib Number</Text>
                  <Text style={styles.bibNumber}>{upcomingRace.bibNumber || '-'}</Text>
                </View>
              </View>
            </TouchableOpacity>
          </Link>
        ) : (
          <View style={styles.emptyCard}>
            <Text style={styles.emptyTitle}>No upcoming registered race</Text>
            <Link href="/races" asChild>
              <TouchableOpacity style={styles.emptyAction}>
                <Text style={styles.emptyActionText}>Browse races</Text>
              </TouchableOpacity>
            </Link>
          </View>
        )}
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Quick Actions</Text>
        <View style={styles.actionsGrid}>
          <Link href="/races" asChild>
            <TouchableOpacity style={styles.actionCard}>
              <Ionicons name="search" size={28} color="#0F766E" />
              <Text style={styles.actionText}>Find Races</Text>
            </TouchableOpacity>
          </Link>
          <Link href="/tracking" asChild>
            <TouchableOpacity style={styles.actionCard}>
              <Ionicons name="navigate" size={28} color="#0F766E" />
              <Text style={styles.actionText}>Start Tracking</Text>
            </TouchableOpacity>
          </Link>
          <Link href="/scanner" asChild>
            <TouchableOpacity style={styles.actionCard}>
              <Ionicons name="qr-code" size={28} color="#0F766E" />
              <Text style={styles.actionText}>Scan QR</Text>
            </TouchableOpacity>
          </Link>
          <Link href="/results" asChild>
            <TouchableOpacity style={styles.actionCard}>
              <Ionicons name="stats-chart" size={28} color="#0F766E" />
              <Text style={styles.actionText}>My Stats</Text>
            </TouchableOpacity>
          </Link>
        </View>
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Recent Results</Text>
        {recentRaces.length > 0 ? (
          recentRaces.map((race) => (
            <View key={race.id} style={styles.resultCard}>
              <View style={styles.resultInfo}>
                <Text style={styles.resultName}>{race.name}</Text>
                <Text style={styles.resultDate}>{race.date}</Text>
              </View>
              <View style={styles.resultStats}>
                <View style={styles.statItem}>
                  <Text style={styles.statValue}>{race.position ? `#${race.position}` : '-'}</Text>
                  <Text style={styles.statLabel}>Position</Text>
                </View>
                <View style={styles.statItem}>
                  <Text style={styles.statValue}>{race.time}</Text>
                  <Text style={styles.statLabel}>Time</Text>
                </View>
              </View>
            </View>
          ))
        ) : (
          <View style={styles.emptyInline}>
            <Text style={styles.emptyInlineText}>No results yet</Text>
          </View>
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
  authContainer: {
    flex: 1,
    backgroundColor: '#FFFFFF',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
  },
  authTitle: {
    fontSize: 32,
    fontWeight: 'bold',
    color: '#0F766E',
    marginTop: 16,
  },
  authSubtitle: {
    fontSize: 16,
    color: '#6B7280',
    marginTop: 8,
    marginBottom: 32,
  },
  primaryButton: {
    backgroundColor: '#0F766E',
    paddingHorizontal: 48,
    paddingVertical: 14,
    borderRadius: 8,
    width: '100%',
    alignItems: 'center',
    marginBottom: 12,
  },
  primaryButtonText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '600',
  },
  secondaryButton: {
    backgroundColor: '#FFFFFF',
    paddingHorizontal: 48,
    paddingVertical: 14,
    borderRadius: 8,
    width: '100%',
    alignItems: 'center',
    borderWidth: 2,
    borderColor: '#0F766E',
  },
  secondaryButtonText: {
    color: '#0F766E',
    fontSize: 16,
    fontWeight: '600',
  },
  welcomeSection: {
    backgroundColor: '#0F766E',
    padding: 24,
    paddingTop: 16,
  },
  welcomeText: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#FFFFFF',
  },
  subtitle: {
    fontSize: 16,
    color: '#A7F3D0',
    marginTop: 4,
  },
  section: {
    padding: 16,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#1F2937',
    marginBottom: 12,
  },
  upcomingCard: {
    backgroundColor: '#FFFFFF',
    borderRadius: 16,
    overflow: 'hidden',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 8,
    elevation: 3,
  },
  raceBanner: {
    width: '100%',
    height: 150,
  },
  raceOverlay: {
    position: 'absolute',
    top: 12,
    right: 12,
  },
  daysCountdown: {
    backgroundColor: '#0F766E',
    borderRadius: 12,
    padding: 12,
    alignItems: 'center',
  },
  daysNumber: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#FFFFFF',
  },
  daysLabel: {
    fontSize: 12,
    color: '#A7F3D0',
  },
  raceInfo: {
    padding: 16,
  },
  raceName: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#1F2937',
    marginBottom: 12,
  },
  raceDetails: {
    gap: 8,
  },
  detailRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  detailText: {
    fontSize: 14,
    color: '#6B7280',
  },
  bibContainer: {
    marginTop: 16,
    padding: 12,
    backgroundColor: '#F0FDF4',
    borderRadius: 8,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  bibLabel: {
    fontSize: 14,
    color: '#6B7280',
  },
  bibNumber: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#0F766E',
  },
  emptyCard: {
    backgroundColor: '#FFFFFF',
    borderRadius: 16,
    padding: 20,
    alignItems: 'center',
    gap: 12,
  },
  emptyTitle: {
    color: '#4B5563',
    fontSize: 15,
  },
  emptyAction: {
    backgroundColor: '#0F766E',
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 10,
  },
  emptyActionText: {
    color: '#FFFFFF',
    fontWeight: '600',
  },
  actionsGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
  },
  actionCard: {
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    padding: 16,
    alignItems: 'center',
    width: '47%',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 4,
    elevation: 2,
  },
  actionText: {
    fontSize: 14,
    fontWeight: '500',
    color: '#1F2937',
    marginTop: 8,
  },
  resultCard: {
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.05,
    shadowRadius: 4,
    elevation: 2,
  },
  resultInfo: {
    flex: 1,
  },
  resultName: {
    fontSize: 16,
    fontWeight: '600',
    color: '#1F2937',
  },
  resultDate: {
    fontSize: 14,
    color: '#6B7280',
    marginTop: 4,
  },
  resultStats: {
    flexDirection: 'row',
    gap: 16,
  },
  statItem: {
    alignItems: 'center',
  },
  statValue: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#0F766E',
  },
  statLabel: {
    fontSize: 12,
    color: '#6B7280',
  },
  emptyInline: {
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    padding: 16,
    alignItems: 'center',
  },
  emptyInlineText: {
    fontSize: 14,
    color: '#6B7280',
  },
  bottomSpacing: {
    height: 24,
  },
});
