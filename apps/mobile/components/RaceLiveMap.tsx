import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, AppState, ScrollView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { router, useFocusEffect } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { useTranslation } from 'react-i18next';
import { RaceMap, useMapTheme, type MapCircle, type MapLine, type MapPin } from '@/components/RaceMap';
import { useTheme } from '@/contexts/theme';
import { api } from '@/lib/api';
import { formatNumber, formatTime } from '@/lib/format';
import { isValidCoordinate, regionFor, type Coordinate, type MapRegion } from '@/lib/routeGeometry';
import {
  HEAT_CELL_RADIUS_METERS,
  HEAT_WINDOW_MINUTES,
  MAX_FOLLOWED_ON_MAP,
  MAX_HEAT_CELLS,
  RACE_MAP_REFRESH_MS,
  followedInRace,
  heatFill,
  heatWindowStart,
  latestPosition,
  maxHeatWeight,
} from '@/lib/raceMap';
import type { FavoriteAthlete, HeatCell } from '@/types';

type CheckpointPin = Coordinate & { id: string; name: string };
type FollowedPosition = { athlete: FavoriteAthlete; position: Coordinate | null };

type RaceLiveMapProps = {
  raceId: string;
  canFollow: boolean;
  followedIds: Set<string>;
};

export function RaceLiveMap({ raceId, canFollow, followedIds }: RaceLiveMapProps) {
  const { t } = useTranslation();
  const { colors } = useTheme();
  const [route, setRoute] = useState<Coordinate[]>([]);
  const [checkpoints, setCheckpoints] = useState<CheckpointPin[]>([]);
  const [heat, setHeat] = useState<HeatCell[]>([]);
  const [followed, setFollowed] = useState<FollowedPosition[]>([]);
  const [followedTotal, setFollowedTotal] = useState(0);
  const [initialRegion, setInitialRegion] = useState<MapRegion | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null);
  const followedKey = useMemo(() => [...followedIds].sort().join(','), [followedIds]);
  const active = useRef(true);

  useEffect(() => {
    active.current = true;
    return () => { active.current = false; };
  }, []);

  useEffect(() => {
    Promise.all([
      api.getPublicRaceRoute(raceId).catch(() => [] as Coordinate[]),
      api.getRaceCheckpoints(raceId).catch(() => []),
    ]).then(([routePoints, rows]) => {
      if (!active.current) return;
      setRoute(routePoints);
      setCheckpoints((rows as Record<string, unknown>[])
        .map((row) => ({ id: String(row.id), name: String(row.name ?? ''), latitude: Number(row.latitude), longitude: Number(row.longitude) }))
        .filter(isValidCoordinate));
    });
  }, [raceId]);

  const refresh = useCallback(async () => {
    const since = heatWindowStart();
    try {
      const [cells, favorites] = await Promise.all([
        api.getRaceHeatmap(raceId, since, MAX_HEAT_CELLS),
        canFollow ? api.listFavorites().catch(() => [] as FavoriteAthlete[]) : Promise.resolve([] as FavoriteAthlete[]),
      ]);
      const inRace = followedInRace(favorites, raceId, Number.MAX_SAFE_INTEGER);
      const shown = inRace.slice(0, MAX_FOLLOWED_ON_MAP);
      const positions = await Promise.all(shown.map(async (athlete) => ({
        athlete,
        position: latestPosition(await api.getPublicAthleteTrack(raceId, athlete.participantId, since.toISOString()).catch(() => [])),
      })));
      if (!active.current) return;
      setHeat(cells);
      setFollowed(positions);
      setFollowedTotal(inRace.length);
      setUpdatedAt(new Date());
      setFailed(false);
    } catch {
      if (active.current) setFailed(true);
    }
  }, [raceId, canFollow]);

  useEffect(() => {
    setLoading(true);
    void refresh().finally(() => { if (active.current) setLoading(false); });
  }, [refresh, followedKey]);

  useFocusEffect(useCallback(() => {
    let timer: ReturnType<typeof setInterval> | null = setInterval(() => void refresh(), RACE_MAP_REFRESH_MS);
    const subscription = AppState.addEventListener('change', (state) => {
      if (state === 'active') {
        void refresh();
        timer ??= setInterval(() => void refresh(), RACE_MAP_REFRESH_MS);
      } else if (timer) {
        clearInterval(timer);
        timer = null;
      }
    });
    return () => {
      if (timer) clearInterval(timer);
      subscription.remove();
    };
  }, [refresh]));

  useEffect(() => {
    if (initialRegion) return;
    const anchor = route.length > 1
      ? route
      : heat.length
        ? heat.map(({ latitude, longitude }) => ({ latitude, longitude }))
        : checkpoints;
    const region = regionFor(anchor);
    if (region) setInitialRegion(region);
  }, [route, heat, checkpoints, initialRegion]);

  const maxWeight = maxHeatWeight(heat);
  const mapTheme = useMapTheme();
  const mapLines = useMemo<MapLine[]>(() => [{ id: 'route', coordinates: route, color: '#94A3B8', width: 4 }], [route]);
  const mapCircles = useMemo<MapCircle[]>(() => heat.map((cell) => ({
    id: `${cell.latitude}:${cell.longitude}`,
    center: { latitude: cell.latitude, longitude: cell.longitude },
    radius: HEAT_CELL_RADIUS_METERS,
    fillColor: heatFill(cell.weight, maxWeight),
  })), [heat, maxWeight]);
  const mapPins = useMemo<MapPin[]>(() => [
    ...checkpoints.map((checkpoint) => ({ id: `checkpoint:${checkpoint.id}`, kind: 'checkpoint' as const, coordinate: checkpoint, title: checkpoint.name })),
    ...followed.flatMap(({ athlete, position }) => position ? [{
      id: `athlete:${athlete.participantId}`,
      kind: 'athlete' as const,
      coordinate: position,
      label: athlete.bibNumber || undefined,
      title: athlete.participantName,
      description: athlete.lastCheckpoint,
    }] : []),
  ], [checkpoints, followed]);
  const openAthlete = (athlete: FavoriteAthlete) =>
    router.push({ pathname: '/athlete/[raceId]/[participantId]', params: { raceId, participantId: athlete.participantId } });
  const minutes = formatNumber(HEAT_WINDOW_MINUTES);

  if (loading && !initialRegion) {
    return <ActivityIndicator style={styles.loading} color={colors.primary} accessibilityLabel={t('mobile:raceLive.loading')} />;
  }

  return (
    <ScrollView contentContainerStyle={styles.content}>
      {initialRegion ? (
        <View style={styles.map}>
          <RaceMap
            initialRegion={initialRegion}
            lines={mapLines}
            circles={mapCircles}
            pins={mapPins}
            theme={mapTheme}
            accessibilityLabel={t('mobile:raceLive.mapLabel')}
            onPinPress={(id) => {
              const athlete = followed.find(({ athlete: row }) => `athlete:${row.participantId}` === id)?.athlete;
              if (athlete) openAthlete(athlete);
            }}
            fill
          />
        </View>
      ) : (
        <View style={[styles.noMap, { backgroundColor: colors.cardBackground }]}>
          <Ionicons name="map-outline" size={28} color={colors.subText} />
          <Text style={[styles.hint, { color: colors.subText, textAlign: 'center' }]}>{t('mobile:raceLive.mapEmpty')}</Text>
        </View>
      )}

      <View style={styles.legend} accessibilityLiveRegion="polite">
        <View style={styles.legendRow}>
          <View style={[styles.swatch, { backgroundColor: heatFill(1, 1) }]} />
          <Text style={[styles.hint, styles.flex, { color: colors.subText }]}>{t('mobile:raceLive.mapDensity', { minutes })}</Text>
        </View>
        <Text style={[styles.hint, { color: colors.subText }]}>{t('mobile:raceLive.mapPrivacy')}</Text>
        {updatedAt ? (
          <Text style={[styles.hint, { color: colors.subText }]}>
            {t('mobile:raceLive.updatedAt', { time: formatTime(updatedAt, { hour: '2-digit', minute: '2-digit', second: '2-digit' }) })}
          </Text>
        ) : null}
        {failed ? (
          <TouchableOpacity onPress={() => void refresh()} accessibilityRole="button" style={styles.retry}>
            <Ionicons name="refresh" size={16} color="#B45309" />
            <Text style={styles.retryText}>{t('mobile:raceLive.mapLoadFailed')}</Text>
          </TouchableOpacity>
        ) : null}
      </View>

      {canFollow ? (
        <View style={styles.followed}>
          <Text style={[styles.sectionTitle, { color: colors.text }]} accessibilityRole="header">{t('mobile:raceLive.mapFollowedTitle')}</Text>
          {followed.length === 0 ? (
            <Text style={[styles.hint, { color: colors.subText }]}>{t('mobile:raceLive.mapFollowHint')}</Text>
          ) : followed.map(({ athlete, position }) => (
            <TouchableOpacity
              key={athlete.participantId}
              style={[styles.followedRow, { backgroundColor: colors.cardBackground }]}
              onPress={() => openAthlete(athlete)}
              accessibilityRole="button"
              accessibilityLabel={t('mobile:raceLive.openAthlete', { name: athlete.participantName ?? athlete.bibNumber ?? '' })}
            >
              <View style={[styles.dot, { backgroundColor: position ? colors.primary : colors.border }]} />
              <View style={styles.flex}>
                <Text style={[styles.name, { color: colors.text }]}>
                  {athlete.participantName}{athlete.bibNumber ? ` #${athlete.bibNumber}` : ''}
                </Text>
                <Text style={[styles.hint, { color: colors.subText }]}>
                  {position
                    ? athlete.lastCheckpoint ? t('mobile:raceLive.lastCheckpoint', { checkpoint: athlete.lastCheckpoint }) : t('mobile:raceLive.mapOnMap')
                    : t('mobile:raceLive.mapNoPosition')}
                </Text>
              </View>
              <Ionicons name="chevron-forward" size={18} color={colors.subText} />
            </TouchableOpacity>
          ))}
          {followedTotal > followed.length ? (
            <Text style={[styles.hint, { color: colors.subText }]}>
              {t('mobile:raceLive.mapFollowedLimit', { shown: formatNumber(followed.length), total: formatNumber(followedTotal) })}
            </Text>
          ) : null}
        </View>
      ) : null}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  content: { padding: 16, paddingTop: 8, gap: 12 },
  loading: { marginTop: 32 },
  map: { height: 360, borderRadius: 16, overflow: 'hidden' },
  noMap: { height: 140, borderRadius: 16, alignItems: 'center', justifyContent: 'center', gap: 6, padding: 16 },
  legend: { gap: 6 },
  legendRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  swatch: { width: 16, height: 16, borderRadius: 8 },
  hint: { fontSize: 13 },
  flex: { flex: 1 },
  retry: { flexDirection: 'row', alignItems: 'center', gap: 6, paddingVertical: 4 },
  retryText: { color: '#B45309', fontSize: 13, fontWeight: '600' },
  followed: { gap: 8 },
  sectionTitle: { fontSize: 16, fontWeight: '700' },
  followedRow: { flexDirection: 'row', alignItems: 'center', gap: 12, borderRadius: 14, padding: 12 },
  dot: { width: 12, height: 12, borderRadius: 6 },
  name: { fontSize: 15, fontWeight: '700' },
});
