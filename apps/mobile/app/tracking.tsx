import { View, Text, StyleSheet, Alert, ActivityIndicator, AppState, TouchableOpacity } from 'react-native';
import { router, useLocalSearchParams } from 'expo-router';
import { useCallback, useEffect, useMemo, useState } from 'react';
import * as Location from 'expo-location';
import { useTrackingStore } from '@/store/tracking';
import { api, getNonEmptyRouteId } from '@/lib/api';
import { RaceMap, useMapTheme, type MapLine, type MapPin } from '@/components/RaceMap';
import type { Checkpoint, Race } from '@/types';
import { freshSosFix } from '@/lib/sos';
import { HUD } from '@/components/HUD';
import { BottomDrawer } from '@/components/BottomDrawer';
import { Ionicons } from '@expo/vector-icons';
import { useTheme } from '@/contexts/theme';
import { CapabilityGate } from '@/components/CapabilityGate';
import { useTranslation } from 'react-i18next';
import { isValidCoordinate, regionFor, type Coordinate, type MapRegion } from '@/lib/routeGeometry';
import { formatDistanceKm } from '@/lib/format';

function normalizeRaceStatus(raw?: string): Race['status'] {
  const value = String(raw || '').toLowerCase();
  if (value === 'registration_open') return 'registration_open';
  if (value === 'registration_closed') return 'registration_closed';
  if (value === 'in_progress') return 'in_progress';
  if (value === 'completed') return 'completed';
  if (value === 'cancelled') return 'cancelled';
  return 'upcoming';
}

function mapCheckpointType(type?: string): Checkpoint['type'] {
  const value = String(type || '').toLowerCase();
  if (value === 'start') return 'start';
  if (value === 'finish') return 'finish';
  if (value === 'water_station') return 'water_station';
  if (value === 'medical') return 'medical';
  return 'checkpoint';
}

function formatTime(seconds: number) {
  const hrs = Math.floor(seconds / 3600);
  const mins = Math.floor((seconds % 3600) / 60);
  const secs = seconds % 60;
  return `${hrs.toString().padStart(2, '0')}:${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
}

export default function TrackingScreen() {
  return (
    <CapabilityGate capability="ATHLETE">
      <TrackingScreenContent />
    </CapabilityGate>
  );
}

function TrackingScreenContent() {
  const { raceId } = useLocalSearchParams<{ raceId?: string }>();
  const routeRaceId = getNonEmptyRouteId(raceId);
  const { isTracking, liveStats, locations, startTracking, stopTracking, currentRace, batterySaver, setBatterySaver, rehydrateFromLocalDb } = useTrackingStore();
  const [elapsedTime, setElapsedTime] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [isSendingSos, setIsSendingSos] = useState(false);
  const [selectedRace, setSelectedRace] = useState<Race | null>(currentRace);
  const { colors } = useTheme();
  const { t } = useTranslation();

  useEffect(() => {
    const subscription = AppState.addEventListener('change', (nextAppState) => {
      if (nextAppState === 'active' && isTracking && currentRace?.id) {
        rehydrateFromLocalDb(currentRace.id).catch(console.error);
      }
    });

    return () => {
      subscription.remove();
    };
  }, [isTracking, currentRace, rehydrateFromLocalDb]);

  useEffect(() => {
    let interval: ReturnType<typeof setInterval> | undefined;
    if (isTracking) {
      interval = setInterval(() => {
        setElapsedTime((prev) => prev + 1);
      }, 1000);
    }
    return () => {
      if (interval) {
        clearInterval(interval);
      }
    };
  }, [isTracking]);

  useEffect(() => {
    let mounted = true;

    const loadRace = async () => {
      const targetRaceId = routeRaceId || getNonEmptyRouteId(currentRace?.id);
      if (!targetRaceId) {
        if (mounted) setIsLoading(false);
        return;
      }

      try {
        const [raceResponseRaw, checkpointsResponse, coursePoints] = await Promise.all([
          api.getRace(targetRaceId),
          api.getRaceCheckpoints(targetRaceId),
          api.getPublicRaceRoute(targetRaceId).catch(() => []),
        ]);
        const raceResponse = raceResponseRaw as any;

        if (!mounted) return;

        const checkpointList = (checkpointsResponse as any[])
          .sort((a, b) => Number(a.orderIndex || 0) - Number(b.orderIndex || 0))
          .map((checkpoint) => ({
            id: String(checkpoint.id),
            name: checkpoint.name,
            distanceFromStart: Number(checkpoint.distanceFromStartKm || 0),
            location: {
              latitude: Number(checkpoint.latitude || raceResponse.startLocationLat || 0),
              longitude: Number(checkpoint.longitude || raceResponse.startLocationLng || 0),
            },
            cutoffTime: checkpoint.cutoffTime,
            type: mapCheckpointType(checkpoint.checkpointType),
          })) as Checkpoint[];

        const routeCoordinates = coursePoints.length > 1
          ? coursePoints
          : checkpointList.map((checkpoint) => checkpoint.location).filter(isValidCoordinate);

        const mappedRace: Race = {
          id: String(raceResponse.id),
          name: raceResponse.name,
          description: raceResponse.description,
          date: String(raceResponse.raceDate || ''),
          startTime: String(raceResponse.startTime || ''),
          location: [raceResponse.city, raceResponse.country].filter(Boolean).join(', '),
          distanceKm: Number(raceResponse.distanceKm || 0),
          distance: Number(raceResponse.distanceKm || 0),
          status: normalizeRaceStatus(raceResponse.status),
          categories: [],
          checkpoints: checkpointList,
          routeCoordinates,
          bannerImage: raceResponse.bannerImageUrl,
          organizer: '',
          registrationDeadline: raceResponse.registrationCloses,
          maxParticipants: raceResponse.maxParticipants,
          currentParticipants: raceResponse.participantCount,
        };

        setSelectedRace(mappedRace);
      } catch {
        if (mounted) {
          setSelectedRace(null);
        }
      } finally {
        if (mounted) setIsLoading(false);
      }
    };

    loadRace();

    return () => {
      mounted = false;
    };
  }, [routeRaceId, currentRace]);

  const handleStartTracking = async () => {
    if (!selectedRace) {
      Alert.alert(t('mobile:ui.tracking.noRaceSelected'), t('mobile:ui.tracking.chooseARaceFromMyRaces'));
      return;
    }

    try {
      await startTracking(selectedRace);
    } catch {
      Alert.alert(t('mobile:ui.tracking.error'), t('mobile:ui.tracking.failedToStartTrackingPleaseEnable'));
    }
  };

  const handleStopTracking = () => {
    Alert.alert(
      t('mobile:trackingScreen.stopTitle'),
      t('mobile:trackingScreen.stopBody'),
      [
        { text: t('mobile:trackingScreen.cancel'), style: 'cancel' },
        {
          text: t('mobile:trackingScreen.stop'),
          style: 'destructive',
          onPress: () => {
            stopTracking();
            setElapsedTime(0);
            router.back();
          },
        },
      ],
    );
  };

  const routePoints = useMemo(() => selectedRace?.routeCoordinates || [], [selectedRace]);
  const checkpointList = useMemo(() => selectedRace?.checkpoints || [], [selectedRace]);

  const initialRegion = useMemo(() => {
    const latest = locations.length > 0 ? locations[locations.length - 1] : null;
    if (latest && isValidCoordinate(latest)) {
      return { latitude: latest.latitude, longitude: latest.longitude, latitudeDelta: 0.02, longitudeDelta: 0.02 };
    }
    return regionFor(routePoints.length ? routePoints : checkpointList.map((checkpoint) => checkpoint.location)) ?? WORLD_REGION;
  }, [locations, routePoints, checkpointList]);
  const [recenterKey, setRecenterKey] = useState(0);
  const [devicePosition, setDevicePosition] = useState<Coordinate | null>(null);
  const refreshDevicePosition = useCallback(async () => {
    try {
      const permission = await Location.getForegroundPermissionsAsync();
      if (!permission.granted) return;
      const position = await Location.getLastKnownPositionAsync()
        ?? await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
      if (position) setDevicePosition({ latitude: position.coords.latitude, longitude: position.coords.longitude });
    } catch {
      setDevicePosition(null);
    }
  }, []);
  useEffect(() => {
    void refreshDevicePosition();
  }, [refreshDevicePosition]);
  const userLocation = useMemo(() => {
    const latest = locations.length > 0 ? locations[locations.length - 1] : null;
    return latest && isValidCoordinate(latest) ? { latitude: latest.latitude, longitude: latest.longitude } : devicePosition;
  }, [locations, devicePosition]);
  const mapTheme = useMapTheme();
  const mapLines = useMemo<MapLine[]>(() => [
    { id: 'route', coordinates: routePoints, color: colors.primary, width: 4 },
    { id: 'track', coordinates: locations.map((loc) => ({ latitude: loc.latitude, longitude: loc.longitude })), color: colors.secondary, width: 3 },
  ], [routePoints, locations, colors.primary, colors.secondary]);
  const mapPins = useMemo<MapPin[]>(() => checkpointList
    .filter((checkpoint) => isValidCoordinate(checkpoint.location))
    .map((checkpoint) => ({
      id: `checkpoint:${checkpoint.id}`,
      kind: 'checkpoint' as const,
      coordinate: checkpoint.location,
      title: checkpoint.name,
      description: formatDistanceKm(Number(checkpoint.distanceFromStart || 0)),
    })), [checkpointList]);

  const sendSos = async (targetRaceId: string) => {
    setIsSendingSos(true);
    try {
      const sosFix = freshSosFix(useTrackingStore.getState().locations);
      const receipt = await api.reportEmergency(targetRaceId, {
        ...(sosFix ?? {}),
        message: t('mobile:sos.message'),
      });
      const delivered = receipt?.deliveryStatus === 'DISPATCHED';
      const locationNote = sosFix ? '' : ` ${t('mobile:sos.noLocation')}`;
      if (delivered) {
        Alert.alert(t('mobile:sos.deliveredTitle'), `${t('mobile:sos.deliveredBody')}${locationNote}`);
      } else {
        Alert.alert(t('mobile:sos.receivedTitle'),
          `${t('mobile:sos.receivedBody')}${locationNote}\n\n${t('mobile:sos.guidance')}`);
      }
    } catch (error) {
      Alert.alert(t('mobile:sos.failedTitle'),
        error instanceof Error ? error.message : t('mobile:sos.failedBody'));
    } finally {
      setIsSendingSos(false);
    }
  };

  const handleSos = () => {
    const targetRaceId = routeRaceId || getNonEmptyRouteId(currentRace?.id) || getNonEmptyRouteId(selectedRace?.id);
    if (!targetRaceId) {
      Alert.alert(t('mobile:sos.noRaceTitle'), t('mobile:sos.noRaceBody'));
      return;
    }
    const sosFix = freshSosFix(locations);
    Alert.alert(
      t('mobile:sos.confirmTitle'),
      sosFix ? t('mobile:sos.confirmBodyWithLocation') : t('mobile:sos.confirmBodyWithoutLocation'),
      [
        { text: t('common:actions.cancel'), style: 'cancel' },
        { text: t('mobile:sos.send'), style: 'destructive', onPress: () => void sendSos(targetRaceId) },
      ],
    );
  };

  if (isLoading) {
    return (
      <View style={[styles.center, { backgroundColor: colors.background }]}>
        <ActivityIndicator size="large" color={colors.primary} />
      </View>
    );
  }

  return (
    <View style={[styles.container, { backgroundColor: colors.background }]}>
      <RaceMap
        initialRegion={initialRegion}
        lines={mapLines}
        pins={mapPins}
        userLocation={userLocation}
        showsUserLocation
        followUser={isTracking}
        recenterKey={recenterKey}
        theme={mapTheme}
        accessibilityLabel={t('mobile:trackingScreen.mapLabel')}
        fill
      />

      <HUD
        onClose={() => router.back()}
        onMyLocation={() => {
          void (isTracking ? Promise.resolve() : refreshDevicePosition()).then(() => setRecenterKey((value) => value + 1));
        }}
        onScan={() => router.push('/scanner')}
        onStop={handleStopTracking}
        isTracking={isTracking}
      />

      <TouchableOpacity
        style={[styles.sosButton, { borderColor: colors.cardBackground }]}
        onPress={handleSos}
        disabled={isSendingSos}
        accessibilityRole="button"
        accessibilityLabel={t('mobile:sos.send')}
      >
        {isSendingSos ? (
          <ActivityIndicator color="#FFFFFF" />
        ) : (
          <>
            <Ionicons name="alert-circle" size={20} color="#FFFFFF" />
            <Text style={styles.sosText}>{t('mobile:ui.tracking.sos')}</Text>
          </>
        )}
      </TouchableOpacity>

      <BottomDrawer
        raceName={selectedRace?.name}
        elapsedTime={formatTime(liveStats?.elapsedTime || elapsedTime)}
        distance={(liveStats?.distanceCovered || 0).toFixed(2)}
        pace={(liveStats?.currentPace || 0).toFixed(2)}
        progress={(liveStats?.distanceCovered || 0) / (selectedRace?.distance || 1)}
        isTracking={isTracking}
        batterySaver={batterySaver}
        onBatterySaverChange={setBatterySaver}
        onStartTracking={handleStartTracking}
      />
    </View>
  );
}

const WORLD_REGION: MapRegion = { latitude: 0, longitude: 20, latitudeDelta: 100, longitudeDelta: 100 };

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  sosButton: {
    position: 'absolute',
    top: 16,
    right: 16,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    backgroundColor: '#DC2626',
    borderRadius: 24,
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderWidth: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.3,
    shadowRadius: 4,
    elevation: 4,
  },
  sosText: {
    color: '#FFFFFF',
    fontWeight: '800',
    fontSize: 14,
    letterSpacing: 1,
  },
});
