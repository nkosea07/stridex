import { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, AppState, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useFocusEffect, useLocalSearchParams } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { useTranslation } from 'react-i18next';
import MapView, { Marker, Polyline, PROVIDER_DEFAULT } from '@/components/Map';
import { RequestErrorState } from '@/components/RequestErrorState';
import { useTheme } from '@/contexts/theme';
import { api, getApiErrorKind, getNonEmptyRouteId, type ApiErrorKind } from '@/lib/api';
import { formatDistanceKm, formatDuration, formatPace, statusTranslationKey } from '@/lib/format';
import { isValidCoordinate, mergeTrack, minutesSince, regionFor, type Coordinate, type MapRegion, type TrackPoint } from '@/lib/routeGeometry';
import type { PublicAthleteTracking } from '@/types';

const REFRESH_INTERVAL_MS = 30000;

type CheckpointPin = Coordinate & { id: string; name: string };

export default function AthleteLiveScreen() {
  const { t } = useTranslation();
  const { colors } = useTheme();
  const params = useLocalSearchParams<{ raceId?: string; participantId?: string }>();
  const raceId = getNonEmptyRouteId(params.raceId);
  const participantId = getNonEmptyRouteId(params.participantId);

  const [summary, setSummary] = useState<PublicAthleteTracking | null>(null);
  const [track, setTrack] = useState<TrackPoint[]>([]);
  const [route, setRoute] = useState<Coordinate[]>([]);
  const [checkpoints, setCheckpoints] = useState<CheckpointPin[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [errorKind, setErrorKind] = useState<ApiErrorKind | null>(null);
  const [now, setNow] = useState(Date.now());
  const [initialRegion, setInitialRegion] = useState<MapRegion | null>(null);
  const lastPointTime = useRef<string | null>(null);

  const loadAll = useCallback(async () => {
    if (!raceId || !participantId) return;
    setErrorKind(null);
    try {
      const [tracking, points, routePoints, checkpointRows] = await Promise.all([
        api.getPublicAthleteTracking(raceId, participantId),
        api.getPublicAthleteTrack(raceId, participantId),
        api.getPublicRaceRoute(raceId).catch(() => [] as Coordinate[]),
        api.getRaceCheckpoints(raceId).catch(() => []),
      ]);
      setSummary(tracking);
      setTrack(points);
      lastPointTime.current = points.length ? points[points.length - 1].time : null;
      setRoute(routePoints);
      const pins = (checkpointRows as any[])
        .map((row) => ({
          id: String(row.id),
          name: String(row.name ?? ''),
          latitude: Number(row.latitude),
          longitude: Number(row.longitude),
        }))
        .filter(isValidCoordinate);
      setCheckpoints(pins);
      setInitialRegion((current) => current ?? regionFor(routePoints.length > 1 ? routePoints : points.length ? points : pins));
      setNow(Date.now());
    } catch (error) {
      setErrorKind(getApiErrorKind(error));
    }
  }, [raceId, participantId]);

  const refreshLive = useCallback(async () => {
    if (!raceId || !participantId) return;
    try {
      const [tracking, points] = await Promise.all([
        api.getPublicAthleteTracking(raceId, participantId),
        api.getPublicAthleteTrack(raceId, participantId, lastPointTime.current),
      ]);
      setSummary(tracking);
      if (points.length) {
        setTrack((current) => {
          const merged = mergeTrack(current, points);
          lastPointTime.current = merged.length ? merged[merged.length - 1].time : lastPointTime.current;
          return merged;
        });
      }
      setErrorKind(null);
    } catch (error) {
      setErrorKind(getApiErrorKind(error));
    } finally {
      setNow(Date.now());
    }
  }, [raceId, participantId]);

  useEffect(() => {
    setLoading(true);
    void loadAll().finally(() => setLoading(false));
  }, [loadAll]);

  useFocusEffect(useCallback(() => {
    let timer: ReturnType<typeof setInterval> | null = null;
    const start = () => {
      if (!timer) timer = setInterval(() => void refreshLive(), REFRESH_INTERVAL_MS);
    };
    const stop = () => {
      if (timer) clearInterval(timer);
      timer = null;
    };
    start();
    const subscription = AppState.addEventListener('change', (state) => {
      if (state === 'active') {
        void refreshLive();
        start();
      } else {
        stop();
      }
    });
    return () => {
      stop();
      subscription.remove();
    };
  }, [refreshLive]));

  const onRefresh = useCallback(() => {
    setRefreshing(true);
    void refreshLive().finally(() => setRefreshing(false));
  }, [refreshLive]);

  const latest = track.length ? track[track.length - 1] : null;
  const lastSeenMinutes = minutesSince(latest?.time, now);

  if (!raceId || !participantId) {
    return <RequestErrorState resource="race" kind="not_found" onRetry={() => undefined} />;
  }

  if (loading) {
    return (
      <View style={[styles.center, { backgroundColor: colors.background }]}>
        <ActivityIndicator size="large" color={colors.primary} accessibilityLabel={t('mobile:athleteLive.loading')} />
      </View>
    );
  }

  if (errorKind && !summary) {
    return <RequestErrorState resource="race" kind={errorKind} onRetry={() => void loadAll()} />;
  }

  return (
    <ScrollView
      style={[styles.container, { backgroundColor: colors.background }]}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
    >
      <View style={styles.header}>
        <Text style={[styles.name, { color: colors.text }]} accessibilityRole="header">
          {summary?.participantName || t('mobile:following.athlete')}
          {summary?.bibNumber ? ` #${summary.bibNumber}` : ''}
        </Text>
        {summary?.status ? (
          <Text style={[styles.status, { color: colors.primary }]}>
            {t(statusTranslationKey(summary.status), { defaultValue: summary.status })}
          </Text>
        ) : null}
        <Text style={[styles.lastSeen, { color: colors.subText }]} accessibilityLiveRegion="polite">
          {lastSeenMinutes === null
            ? t('mobile:athleteLive.noLocation')
            : lastSeenMinutes === 0
              ? t('mobile:athleteLive.seenJustNow')
              : t('mobile:athleteLive.seenMinutesAgo', { count: lastSeenMinutes })}
        </Text>
      </View>

      {errorKind && <RequestErrorState resource="race" kind={errorKind} onRetry={() => void refreshLive()} compact />}

      {initialRegion ? (
        <MapView style={styles.map} provider={PROVIDER_DEFAULT} initialRegion={initialRegion}
          accessibilityLabel={t('mobile:athleteLive.mapLabel')}>
          {route.length > 1 && <Polyline coordinates={route} strokeColor="#94A3B8" strokeWidth={4} />}
          {track.length > 1 && (
            <Polyline coordinates={track.map(({ latitude, longitude }) => ({ latitude, longitude }))}
              strokeColor={colors.primary} strokeWidth={4} />
          )}
          {checkpoints.map((checkpoint) => (
            <Marker key={checkpoint.id} coordinate={checkpoint} title={checkpoint.name}>
              <View style={[styles.checkpointPin, { backgroundColor: colors.cardBackground, borderColor: colors.primary }]}>
                <Ionicons name="flag" size={12} color={colors.primary} />
              </View>
            </Marker>
          ))}
          {latest && (
            <Marker coordinate={latest} title={summary?.participantName ?? undefined}
              description={summary?.lastCheckpoint ?? undefined}>
              <View style={[styles.athletePin, { backgroundColor: colors.primary }]}>
                <Text style={styles.athletePinText}>{summary?.bibNumber || '•'}</Text>
              </View>
            </Marker>
          )}
        </MapView>
      ) : (
        <View style={[styles.noMap, { backgroundColor: colors.cardBackground }]}>
          <Ionicons name="map-outline" size={28} color={colors.subText} />
          <Text style={[styles.noMapText, { color: colors.subText }]}>{t('mobile:athleteLive.noMap')}</Text>
        </View>
      )}

      <View style={styles.statsGrid}>
        {[
          { key: 'lastCheckpoint', value: summary?.lastCheckpoint },
          { key: 'elapsed', value: summary?.elapsedTime },
          { key: 'pace', value: summary?.averagePace },
          { key: 'predictedFinish', value: summary?.predictedFinishTime },
          {
            key: 'remaining',
            value: summary?.distanceRemaining !== null && summary?.distanceRemaining !== undefined
              ? formatDistanceKm(Number(summary.distanceRemaining))
              : null,
          },
        ].map((stat) => (
          <View key={stat.key} style={[styles.statCard, { backgroundColor: colors.cardBackground }]}>
            <Text style={[styles.statValue, { color: colors.text }]}>{stat.value || '-'}</Text>
            <Text style={[styles.statLabel, { color: colors.subText }]}>{t(`mobile:athleteLive.stats.${stat.key}`)}</Text>
          </View>
        ))}
      </View>

      <View style={styles.section}>
        <Text style={[styles.sectionTitle, { color: colors.text }]} accessibilityRole="header">
          {t('mobile:athleteLive.splits')}
        </Text>
        {summary?.splits.length ? summary.splits.map((split, index) => (
          <View key={`${split.name}-${index}`} style={[styles.splitRow, { borderBottomColor: colors.border }]}>
            <Text style={[styles.splitName, { color: colors.text }]}>{split.name}</Text>
            <Text style={[styles.splitValue, { color: colors.subText }]}>
              {split.durationSeconds !== null && split.durationSeconds !== undefined ? formatDuration(split.durationSeconds) : split.time || '-'}
              {split.paceSecondsPerKm ? ` · ${t('mobile:trackingScreen.paceValue', { pace: formatPace(split.paceSecondsPerKm) })}` : ''}
            </Text>
          </View>
        )) : (
          <Text style={[styles.noMapText, { color: colors.subText }]}>{t('mobile:athleteLive.noSplits')}</Text>
        )}
      </View>
      <Text style={[styles.footnote, { color: colors.subText }]}>{t('mobile:athleteLive.refreshNote')}</Text>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  header: { padding: 16, gap: 4 },
  name: { fontSize: 22, fontWeight: '700' },
  status: { fontSize: 14, fontWeight: '600' },
  lastSeen: { fontSize: 13 },
  map: { height: 320, marginHorizontal: 16, borderRadius: 16, overflow: 'hidden' },
  noMap: { height: 120, marginHorizontal: 16, borderRadius: 16, alignItems: 'center', justifyContent: 'center', gap: 6, padding: 16 },
  noMapText: { fontSize: 14, textAlign: 'center' },
  checkpointPin: { width: 24, height: 24, borderRadius: 12, borderWidth: 2, alignItems: 'center', justifyContent: 'center' },
  athletePin: { minWidth: 34, height: 34, borderRadius: 17, paddingHorizontal: 6, alignItems: 'center', justifyContent: 'center', borderWidth: 2, borderColor: '#FFFFFF' },
  athletePinText: { color: '#FFFFFF', fontSize: 12, fontWeight: '700' },
  statsGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 10, padding: 16 },
  statCard: { width: '47%', borderRadius: 12, padding: 12 },
  statValue: { fontSize: 18, fontWeight: '700' },
  statLabel: { fontSize: 12, marginTop: 2 },
  section: { paddingHorizontal: 16 },
  sectionTitle: { fontSize: 17, fontWeight: '700', marginBottom: 8 },
  splitRow: { flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 10, borderBottomWidth: StyleSheet.hairlineWidth },
  splitName: { fontSize: 14, fontWeight: '600', flexShrink: 1 },
  splitValue: { fontSize: 14 },
  footnote: { fontSize: 12, padding: 16, textAlign: 'center' },
});
